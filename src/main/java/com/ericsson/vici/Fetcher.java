/*
   Copyright 2017 Ericsson AB.
   For a full list of individual contributors, please see the commit history.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.ericsson.vici;


import com.ericsson.vici.api.entities.Preferences;
import com.ericsson.vici.api.entities.Query;
import com.ericsson.vici.entities.*;
import com.ericsson.vici.entities.Eiffel.CDEvent;
import com.ericsson.vici.entities.Eiffel.CustomData;
import com.ericsson.vici.entities.Eiffel.EiffelEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ericsson.vici.ViciApplication.log;
import static com.ericsson.vici.api.ApiController.getTarget;
import static com.ericsson.vici.entities.Event.REDIRECT;
import static com.ericsson.vici.entities.EventData.CANCELED;
import static com.ericsson.vici.entities.EventData.FINISHED;
import static com.ericsson.vici.entities.EventData.STARTED;
import static com.ericsson.vici.entities.EventData.TRIGGERED;

public class Fetcher {
    public static final String TEST_CASE = "TestCase";
    public static final String ACTIVITY = "Activity";
    public static final String TEST_SUITE = "TestSuite";
    public static final String PIPELINE_RUN = "PipelineRun";
    public static final String ARTIFACT = "Artifact";
    public static final String SERVICE = "Service";

    public static final String DEFAULT = "Default";

    private static final Pattern CUSTOMDATA_KEY_PATTERN = Pattern.compile("^\\(key=(.*)\\)");

    private static HashMap<String, EventCache> eventCaches = new HashMap<>(); // TODO: a job that removes very old caches för memory cleanup
    private static HashMap<String, CDEventCache>  cdEventCaches = new HashMap<>();
    private static ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).registerModule(new JavaTimeModule());

    private int beforeCheck = 0;
    public Fetcher() {
    }

    private String getValueFromKey(EiffelEvent event, String key) {
        if (key == null || key.trim().equals("")) {
            return null;
        }

        String[] prefixSplit = key.split("@");
        String prefix = "";
        if (prefixSplit.length > 1) {
            prefix = prefixSplit[0];
            key = prefixSplit[1];
        }
        String[] keySplit = key.split("\\.");

        switch (keySplit[0]) {
            case "data":
                switch (keySplit[1]) {
                    case "name":
                        return prefix + event.getData().getName();
                    case "heading":
                        return prefix + event.getData().getHeading();
                    case "gav":
                        switch (keySplit[2]) {
                            case "artifactId":
                                return prefix + event.getData().getGav().getArtifactId();
                            default:
                                break;
                        }
                        break;
                    case "gitIdentifier":
                        switch (keySplit[2]) {
                            case "repoName":
                                return prefix + event.getData().getGitIdentifier().getRepoName();
                            default:
                                break;
                        }
                        break;
                    case "testCase":
                        switch (keySplit[2]) {
                            case "id":
                                return prefix + event.getData().getTestCase().getId();
                            default:
                                break;
                        }
                        break;
                    case "customData":
                        Matcher matcher = CUSTOMDATA_KEY_PATTERN.matcher(keySplit[2]);
                        String customDataKey = null;
                        if (matcher.find()) {
                            customDataKey = matcher.group(1);
                        }
                        for (CustomData customData : event.getData().getCustomData()) {
                            if (customDataKey == null || customData.getKey().equals(customDataKey)) {
                                return prefix + customData.getValue();
                            }
                        }
                        break;
                    default:
                        break;
                }
            default:
                break;
        }
        log.error("Aggregation key not implemented: " + key + ". Please add this key to the Fetcher or correct into a valid key.");
        if (prefix.length() > 0) {
            return prefix;
        }
        return null;
    }

    private String getAggregateValue(Event event, Preferences preferences) {
        String key = preferences.getAggregateOn().get(event.getType());
        if (key == null) {
            key = preferences.getAggregateOn().get(DEFAULT);
            if (key == null) {
                key = new Preferences().getAggregateOn().get(event.getType());
                if (key == null) {
                    key = new Preferences().getAggregateOn().get(DEFAULT);
                }
            }
        }

        return getValueFromKey(event.getThisEiffelEvent(), key);
    }

    public Events fetchEvents(Preferences preferences) {
        log.info("Downloading eiffel-events from: " + preferences.getUrl());

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<EiffelEvent[]> responseEntity;
        EiffelEvent[] eiffelEvents = null;

        Pattern pattern = Pattern.compile("^localFile\\[(.+)]$");
        Matcher matcher = pattern.matcher(preferences.getUrl().trim());

        long eventsFetchedAt = System.currentTimeMillis();

        if (matcher.find()) {
//            System.out.println("Request for local file " + matcher.group(1) + ".json");
//            responseEntity = restTemplate.getForEntity("http://127.0.0.1:8080/" + matcher.group(1), EiffelEvent[].class);

            ObjectMapper mapper = new ObjectMapper();
            Resource resource = new ClassPathResource("static/assets/" + matcher.group(1) + ".json");
            InputStream jsonFileStream;

            try {
                jsonFileStream = resource.getInputStream();
                eiffelEvents = mapper.readValue(jsonFileStream, EiffelEvent[].class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Query query = new Query(null, null, 0, Integer.MAX_VALUE, false, null, true);
            ObjectMapper mapper = new ObjectMapper();
            JSONObject queryJson = null;
            try {
                queryJson = new JSONObject(mapper.writeValueAsString(query));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = null;
            if (queryJson != null) {
                entity = new HttpEntity<>(queryJson.toString(), headers);
            }

            responseEntity = restTemplate.exchange(preferences.getUrl(), HttpMethod.POST, entity, EiffelEvent[].class);
            eiffelEvents = responseEntity.getBody();

//            MediaType contentType = responseEntity.getHeaders().getContentType();
//            HttpStatus statusCode = responseEntity.getStatusCode();
        }

        if (eiffelEvents == null) {
            return null;
        }

        log.info("Downloaded eiffel-events. Importing...");

        int total = eiffelEvents.length;
        int count = 0;
        int lastPrint = count;

        long timeStart = Long.MAX_VALUE;
        long timeEnd = Long.MIN_VALUE;

        // Collections.shuffle(Arrays.asList(eiffelEvents)); // for testing a non-ordered set of eiffel data


        HashMap<String, Event> events = new HashMap<>();

        ArrayList<Event> potentialEventToBeMerges = new ArrayList<>();

        // First all trigger events and such (the base events)
        for (EiffelEvent eiffelEvent : eiffelEvents) {
            Event event = new Event(eiffelEvent);

            switch (event.getType()) {
                case "EiffelTestCaseStartedEvent":
                case "EiffelTestCaseFinishedEvent":
                case "EiffelTestCaseCanceledEvent":

                case "EiffelActivityStartedEvent":
                case "EiffelActivityFinishedEvent":
                case "EiffelActivityCanceledEvent":

                case "EiffelTestSuiteFinishedEvent":
                    // Skip at this time
                    break;

                default:
                    switch (event.getType()) {
                        case "EiffelTestCaseTriggeredEvent":
                            event.setType(TEST_CASE);
                            // May be merged into a TestSuite
                            potentialEventToBeMerges.add(event);
                            break;

                        case "EiffelActivityTriggeredEvent":
                            event.setType(ACTIVITY);
                            break;

                        case "EiffelTestSuiteStartedEvent":
                            event.setType(TEST_SUITE);
                            break;

                        default:
                            break;
                    }
                    events.put(event.getId(), event);
                    count++;
                    break;
            }

            // Print progress
            if ((float) count / total > (float) lastPrint / total + 0.2 || count == total || count == 0) {
                log.info(count + "/" + total);
                lastPrint = count;
            }
        }

        // All followup events
        for (EiffelEvent eiffelEvent : eiffelEvents) {
            Event event = new Event(eiffelEvent);

            // If event is not already added, its a followup event
            if (!events.containsKey(event.getId())) {
                Event target = null;

                // Find the target
                ArrayList<Link> tmpLinks = new ArrayList<>();
                for (Link link : event.getLinks()) {
                    if (link.getType().equals("ACTIVITY_EXECUTION") || link.getType().equals("TEST_CASE_EXECUTION") || link.getType().equals("TEST_SUITE_EXECUTION")) {
                        target = events.get(link.getTarget());
                    } else {
                        tmpLinks.add(link);
                    }
                }

                if (target != null) {
                    for (Link link : tmpLinks) {
                        events.get(target.getId()).getLinks().add(link);
                    }

                    events.put(event.getId(), new Event(event, target.getId()));
                    switch (event.getType()) {
                        case "EiffelTestCaseStartedEvent":
                        case "EiffelActivityStartedEvent":
                            target.getEiffelEvents().put(STARTED, event.getEiffelEvents().get(TRIGGERED));
                            target.getTimes().put(STARTED, event.getTimes().get(TRIGGERED));
                            break;
                        case "EiffelTestCaseCanceledEvent":
                        case "EiffelActivityCanceledEvent":
                            target.getEiffelEvents().put(CANCELED, event.getEiffelEvents().get(TRIGGERED));
                            target.getTimes().put(CANCELED, event.getTimes().get(TRIGGERED));
                            break;
                        default:
                            target.getEiffelEvents().put(FINISHED, event.getEiffelEvents().get(TRIGGERED));
                            target.getTimes().put(FINISHED, event.getTimes().get(TRIGGERED));
                            break;
                    }
                } else {
                    log.error("null link while fetching followup events.");
                }
                count++;
                if ((float) count / total > (float) lastPrint / total + 0.2 || count == total || count == 0) {
                    log.info(count + "/" + total);
                    lastPrint = count;
                }
            }
        }

        // Merge test cases into suites
        for (Event event : potentialEventToBeMerges) {
            ArrayList<Link> tmpLinks = new ArrayList<>();
            Event testSuite = null;
            for (Link link : event.getLinks()) {
                if (events.get(link.getTarget()).getType().equals(TEST_SUITE)) {
                    testSuite = events.get(link.getTarget());
                    testSuite.addEvent(event);
                    events.put(event.getId(), new Event(event, testSuite.getId())); // Override with redirect
                } else {
                    tmpLinks.add(link);
                }
            }
            if (testSuite != null) {
                // Pass the test case's links to the test suite
                for (Link link : tmpLinks) {
                    testSuite.getLinks().add(link);
                }
            }
        }

        // Makes the links go both ways.
        log.info("Finding and applying children to all nodes.");
        for (Event event : events.values()) {
            if (!event.getType().equals(REDIRECT)) {
                for (Link link : event.getLinks()) {
                    String target = getTarget(link.getTarget(), events);
                    events.get(target).getChildren().add(new ChildLink(event.getId(), link.getType()));
                }
            }
        }

        Events eventsObject = new Events(events, timeStart, timeEnd, eventsFetchedAt);


        log.info("Events imported from: " + preferences.getUrl());
        return eventsObject;
    }

    public Events getEvents(Preferences preferences) {
        Events events = null;
        EventCache eventCache = eventCaches.get(preferences.getUrl());

        boolean setAggregateOn = true;

        if (eventCache != null && eventCache.getEvents().getTimeCollected() > System.currentTimeMillis() - preferences.getCacheLifeTimeMs()) {
            log.info("Using cached events for: " + preferences.getUrl());
            events = eventCache.getEvents();

            // Checking if we need to reset aggregateOn
            setAggregateOn = false;
            for (String type : preferences.getAggregateOn().keySet()) {
                String stored = eventCache.getPreferences().getAggregateOn().get(type);
                String preferred = preferences.getAggregateOn().get(type);

                if (stored == null || preferred == null) {
                    if (stored == null && preferred == null) {
                        setAggregateOn = true;
                        break;
                    }
                } else if (!stored.equals(preferred)) {
                    setAggregateOn = true;
                    break;
                }
            }
        }

        if (events == null) {
            events = fetchEvents(preferences);
        }

        // Sets aggregate values
        if (setAggregateOn) {
            log.info("Setting aggregation values for: " + preferences.getUrl());
            for (Event event : events.getEvents().values()) {
                if (!event.getType().equals(REDIRECT)) {
                    String value = getAggregateValue(event, preferences);
                    if (value == null) {
                        // Throws error to send it to frontend.
                        String error = "Null aggregation value for: " + event.getType() + ". Please implement in backend.";
                        log.error(error);
                        //throw new RuntimeException(error);
                    }
                    event.setAggregateOn(value);
                }
            }
        }

        eventCaches.put(preferences.getUrl(), new EventCache(events, preferences));
        log.info("Events fetched.");
        return events;
    }


    private String getCDEventAggregateValue(EventData event, Preferences preferences) {
        String key = preferences.getAggregateOn().get(event.getType());
        if (key == null) {
            key = preferences.getAggregateOn().get(DEFAULT);
            if (key == null) {
                key = new Preferences().getAggregateOn().get(event.getType());
                if (key == null) {
                    key = new Preferences().getAggregateOn().get(DEFAULT);
                }
            }
        }
        log.info("GET AGGREGATE on: " + event.getAggregateOn());
        return event.getAggregateOn();
    }

    public CDEvents fetchEventDatas(Preferences preferences) throws URISyntaxException {
        CDEvents CDEvents = null;
        Pattern pattern = Pattern.compile("^localFile\\[(.+)]$");
        Matcher matcher = pattern.matcher(preferences.getUrl().trim());

        long eventsFetchedAt = System.currentTimeMillis();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<CDEvent[]> responseEntity;
        CDEvent[] cdEvents = null;

        if (matcher.find()) {
//            System.out.println("Request for local file " + matcher.group(1) + ".json");
//            responseEntity = restTemplate.getForEntity("http://127.0.0.1:8080/" + matcher.group(1), EiffelEvent[].class);

            ObjectMapper mapper = new ObjectMapper();
            Resource resource = new ClassPathResource("static/assets/" + matcher.group(1) + ".json");
            InputStream jsonFileStream;

            try {
                jsonFileStream = resource.getInputStream();
                cdEvents = mapper.readValue(jsonFileStream, CDEvent[].class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Query query = new Query(null, null, 0, Integer.MAX_VALUE, false, null, true);
            ObjectMapper mapper = new ObjectMapper();
            JSONObject queryJson = null;
            try {
                queryJson = new JSONObject(mapper.writeValueAsString(query));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = null;
            if (queryJson != null) {
                entity = new HttpEntity<>(queryJson.toString(), headers);
            }

            responseEntity = restTemplate.exchange(preferences.getUrl(), HttpMethod.POST, entity, CDEvent[].class);
            cdEvents = responseEntity.getBody();

//            MediaType contentType = responseEntity.getHeaders().getContentType();
//            HttpStatus statusCode = responseEntity.getStatusCode();
        }

        HashMap<String, EventData> events = new HashMap<>();
        long timeStart = Long.MAX_VALUE;
        long timeEnd = Long.MIN_VALUE;

        EventData eventData;

        for (CDEvent cdEvent: cdEvents){
            eventData = new EventData(cdEvent);
            events.put(cdEvent.getId(), eventData);
        }
//        CDEvent cdEvent1 = new CDEvent("id",new URI("cdEvents.dev"),"dev.cdevents.service.deployed.0.1.0", (long) 1677242528.451602000);
//        cdEvents[0] = cdEvent1;
//        for(CDEvent cdEvent: cdEvents) {
//            try {
//                log.info(objectMapper.writeValueAsString(cdEvent));
//            } catch (JsonProcessingException e) {
//                e.printStackTrace();
//            }
//            eventData = new EventData(cdEvent);
////                    events.put(cdEvent.getId(), eventData);
//            switch (eventData.getType()) {
////                case "dev.cdevents.testsuite.started.0.1.0":
////                    eventData.setType(TEST_SUITE);
////                    events.put(eventData.getId(), eventData);
////                    break;
//
////                case "dev.cdevents.pipelinerun.started.0.1.0":
////                    eventData.setType(PIPELINE_RUN);
////                    events.put(eventData.getId(), eventData);
////                    break;
//
//                case "dev.cdevents.artifact.packaged.0.1.0":
//                    eventData.setType(ARTIFACT);
//                    events.put(eventData.getId(), eventData);
//                    break;
//
////                case "dev.cdevents.service.deployed.0.1.0":
////                    eventData.setType(SERVICE);
////                    events.put(eventData.getId(), eventData);
////                    break;
//
//                default:
//                    break;
//            }
//        }
//
//        for (CDEvent cdEvent: cdEvents){
//            eventData = new EventData(cdEvent);
//            try {
//                log.info(objectMapper.writeValueAsString(eventData));
//            } catch (JsonProcessingException e) {
//                e.printStackTrace();
//            }
//            if (!events.containsKey(eventData.getId())) {
//                EventData target = null;
//
//                ArrayList<Link> tmpLinks = new ArrayList<>();
//                log.info(cdEvent.getType());
//                for (Link link : eventData.getLinks()) {
//                    if (link.getType().equals("ACTIVITY_EXECUTION") || link.getType().equals("TEST_CASE_EXECUTION") || link.getType().equals("TEST_SUITE_EXECUTION") || link.getType().equals("CONTEXT") || link.getType().equals("ARTIFACT") || link.getType().equals("CAUSE")) {
//                        //|| link.getType().equals("ARTIFACT") || link.getType().equals("CAUSE")
//                        target = events.get(link.getTarget());
//                        log.info("LINK.getTarget: " + link.getTarget());
//                        log.info("TARGET: " + target.getType());
//                        try {
//                            log.info(objectMapper.writeValueAsString(target));
//                        } catch (JsonProcessingException e) {
//                            e.printStackTrace();
//                        }
//                    } else {
//                        tmpLinks.add(link);
//                    }
//                }
//
//                if (target != null) {
//                    for (Link link : tmpLinks) {
//                        events.get(target.getId()).getLinks().add(link);
//                    }
//
//                    events.put(eventData.getId(), new EventData(eventData, target.getId()));
//                    try {
//                        log.info("EVENTS: " + objectMapper.writeValueAsString(events));
//                    } catch (JsonProcessingException e) {
//                        e.printStackTrace();
//                    }
//                    switch (eventData.getType()) {
//                        case "dev.cdevents.artifact.published.0.1.0":
//                            log.info(target.getType());
//                            log.info(target.getCDEvents().toString());
//                            log.info(eventData.getCDEvents().toString());
//                            target.getCDEvents().put(PUBLISHED, eventData.getCDEvents().get(TRIGGERED));
//                            target.getTimes().put(PUBLISHED, eventData.getTimes().get(TRIGGERED));
//                            break;
//                        case "dev.cdevents.service.deployed.0.1.0":
//                            log.info(target.getType());
//                            log.info(target.getCDEvents().toString());
//                            log.info(eventData.getCDEvents().toString());
//                            target.getCDEvents().put(DEPLOYED, eventData.getCDEvents().get(TRIGGERED));
//                            target.getTimes().put(DEPLOYED, eventData.getTimes().get(TRIGGERED));
//                            break;
//                        case "dev.cdevents.testsuite.started.0.1.0":
//                        case "dev.cdevents.pipelinerun.started.0.1.0":
//                            log.info(target.getType());
//                            log.info(target.getCDEvents().toString());
//                            //eventData.getCDEvents().put(STARTED, eventData.getCDEvents().get(TRIGGERED));]
//                            target.getCDEvents().put(STARTED, eventData.getCDEvents().get(TRIGGERED));
//                            target.getTimes().put(STARTED, eventData.getTimes().get(TRIGGERED));
//                            break;
//                        case "dev.cdevents.pipelinerun.finished.0.1.0":
//                        case "dev.cdevents.testsuite.finished.0.1.0":
//                            log.info(target.getType());
//                            try {
//                                log.info(objectMapper.writeValueAsString(target));
//                            } catch (JsonProcessingException e) {
//                                e.printStackTrace();
//                            }
//                            log.info(target.getCDEvents().toString());
//                            log.info(eventData.getCDEvents().toString());
//                            target.getCDEvents().put(FINISHED, eventData.getCDEvents().get(TRIGGERED));
//                            target.getTimes().put(FINISHED, eventData.getTimes().get(TRIGGERED));
//                            break;
//                        default:
//                            log.error("No followup event");
//                            break;
//                    }
//                } else {
//                    log.error("null link while fetching followup events.");
//                }
//            }
//        }
//
//
//        // Makes the links go both ways.
//        log.info("Finding and applying children to all nodes.");
//        for (EventData event : events.values()) {
//            if (!event.getType().equals(REDIRECT)) {
//                for (Link link : event.getThisCDEvent().getLinks()) {
//                    String target = getCDEventTarget(link.getTarget(), events);
//                    log.info("STRING TARGET: " + target + " for " + event.getType());
//                    log.info(events.get(target).toString());
//                    if(events.get(target).getChildren() != null)
//                        events.get(target).getChildren().add(new ChildLink(event.getId(), link.getType()));
//                }
//            }
//        }

        CDEvents = new CDEvents(events, timeStart, timeEnd, eventsFetchedAt);
//        try {
//            log.info(objectMapper.writeValueAsString(CDEvents));
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
        return CDEvents;
    }

    public CDEvents getEventDatas(Preferences preferences) throws URISyntaxException {
        CDEvents events = null;
        CDEventCache eventCache = cdEventCaches.get(preferences.getUrl());

        boolean setAggregateOn = true;

        if (eventCache != null && eventCache.getEvents().getTimeCollected() > System.currentTimeMillis() - preferences.getCacheLifeTimeMs()) {
            log.info("Using cached events for: " + preferences.getUrl());
            events = eventCache.getEvents();

            // Checking if we need to reset aggregateOn
            setAggregateOn = false;
            for (String type : preferences.getAggregateOn().keySet()) {
                String stored = eventCache.getPreferences().getAggregateOn().get(type);
                String preferred = preferences.getAggregateOn().get(type);

                if (stored == null || preferred == null) {
                    if (stored == null && preferred == null) {
                        setAggregateOn = true;
                        break;
                    }
                } else if (!stored.equals(preferred)) {
                    setAggregateOn = true;
                    break;
                }
            }
        }

        if (events == null) {
            log.info("events = null");
            events = fetchEventDatas(preferences);
        }

        // Sets aggregate values
        if (setAggregateOn) {
            log.info("Setting aggregation values for: " + preferences.getUrl());
            for (EventData event : events.getEvents().values()) {
                if (!event.getType().equals(REDIRECT)) {
                    String value = getCDEventAggregateValue(event, preferences);
                    if (value == null) {
                        // Throws error to send it to frontend.
                        String error = "Null aggregation value for: " + event.getType() + ". Please implement in backend.";
                        log.error(error);
                        //throw new RuntimeException(error);
                    }
                    event.setAggregateOn(value);
                }
            }
        }

        cdEventCaches.put(preferences.getUrl(), new CDEventCache(events, preferences));
        log.info("Events fetched.");
        return events;
    }


    public CDEvent[] getCDEvents(Preferences preferences){
        CDEvents CDEvents = null;
        Pattern pattern = Pattern.compile("^localFile\\[(.+)]$");
        Matcher matcher = pattern.matcher(preferences.getUrl().trim());

        long eventsFetchedAt = System.currentTimeMillis();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<CDEvent[]> responseEntity;
        CDEvent[] cdEvents = null;

        if (matcher.find()) {
//            System.out.println("Request for local file " + matcher.group(1) + ".json");
//            responseEntity = restTemplate.getForEntity("http://127.0.0.1:8080/" + matcher.group(1), EiffelEvent[].class);

            ObjectMapper mapper = new ObjectMapper();
            Resource resource = new ClassPathResource("static/assets/" + matcher.group(1) + ".json");
            InputStream jsonFileStream;

            try {
                jsonFileStream = resource.getInputStream();
                cdEvents = mapper.readValue(jsonFileStream, CDEvent[].class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Query query = new Query(null, null, 0, Integer.MAX_VALUE, false, null, true);
            ObjectMapper mapper = new ObjectMapper();
            JSONObject queryJson = null;
            try {
                queryJson = new JSONObject(mapper.writeValueAsString(query));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = null;
            if (queryJson != null) {
                entity = new HttpEntity<>(queryJson.toString(), headers);
            }

            log.info("URL to invoke for events {}", preferences.getUrl());
            responseEntity = restTemplate.exchange(preferences.getUrl(), HttpMethod.POST, entity, CDEvent[].class);
            cdEvents = responseEntity.getBody();

//            MediaType contentType = responseEntity.getHeaders().getContentType();
//            HttpStatus statusCode = responseEntity.getStatusCode();
        }

        HashMap<String, EventData> events = new HashMap<>();
        long timeStart = Long.MAX_VALUE;
        long timeEnd = Long.MIN_VALUE;
        HashMap<String,String> flowNames = new HashMap<String, String>();
        for(CDEvent event: cdEvents){
            if(flowNames.get(getFlow(event, cdEvents).toString()) == null){
                int flowNumber = flowNames.size()+1;
                flowNames.put(getFlow(event, cdEvents).toString(), "Sequence " + flowNumber);
            }
            event.setSequence(flowNames.get(getFlow(event, cdEvents).toString()));
        }

        return cdEvents;
    }

    public ArrayList<CDEventData> getAggregation(CDEvent[] events) {
        ArrayList<CDEventData> cdEventData = new ArrayList<CDEventData>();
        boolean aggregateOn = false;

        for(CDEvent cdEvent: events){
            for(CDEventData data : cdEventData){
                if(getFlow(data.getCdEvents().get(0), events).equals(getFlow(cdEvent, events))){
                    if(data.getType().equals(cdEvent.getType())) {
                        aggregateOn = true;
                        data.addEvent(cdEvent);
                    }
                }
                else
                    aggregateOn = false;
            }
            if(!aggregateOn){
                cdEventData.add(new CDEventData(cdEvent));
            }
        }
        //aggregateOn = true;
        //data.addEvent(cdEvent);
        return cdEventData;
    }

    public String getTargetType(String targetID, CDEvent[] events){
        String targetType = "";
        for(CDEvent cdEvent: events){
            if (cdEvent.getId().equals(targetID)){
                targetType = cdEvent.getType();
            }
        }
        return targetType;
    }

    public String getAfterFlow(CDEvent cdEvent, CDEvent[] cdEvents) {
        String flow = "";
        flow += cdEvent.getType() + ",";
        for (CDEvent event : cdEvents) {
            if(event.getLinks().size() > 0) {
                if (event.getLinks().get(0).getTarget().equals(cdEvent.getId())) {
                    flow += getAfterFlow(event, cdEvents);
                }
            }
        }
        return flow;
    }

    public String getBeforeFlow(CDEvent cdEvent, CDEvent[] cdEvents){
        String before = "";
        before += cdEvent.getType() + ",";
        //log.info(cdEvent.getType());
        for (CDEvent event : cdEvents) {
            if(cdEvent.getLinks().size() > 0) {
                if(event.getId().equals(cdEvent.getLinks().get(0).getTarget())) {
//                    if(beforeCheck == 0){
//                        before += event.getSource().toString();
//                    }
//                    beforeCheck++;
                    before += getBeforeFlow(event, cdEvents);
                }
            }
            else{
                before += cdEvent.getSource().toString();
            }
        }
        return before;
    }

    public ArrayList<String> getFlow(CDEvent cdEvent, CDEvent[] cdEvents){
        beforeCheck = 0;
        String[] before = getBeforeFlow(cdEvent, cdEvents).split(",");
        String[] after = getAfterFlow(cdEvent, cdEvents).split(",");
        ArrayList<String> flow = new ArrayList<String>();
        for(int i = before.length-1; i >= 0; i--){
            flow.add(before[i]);
        }
        for(String type: after){
            if(!type.equals(after[0]))
                flow.add(type);
        }
        return flow;
    }

    public ArrayList<CDEvent> getEventsByDetail(String detail, CDEvent[] cdEvents, CDEvent target){
        ArrayList<CDEvent> detailEvents = new ArrayList<CDEvent>();
        switch (detail){
            case "source":
                for(CDEvent event: cdEvents){
                    if(event.getSource().toString().equals(target.getSource().toString())){
                        detailEvents.add(event);
                    }
                }
                break;
            case "type":
                for(CDEvent event: cdEvents){
                    if(event.getType().equals(target.getType().toString())){
                        detailEvents.add(event);
                    }
                }
                break;
            case "sequence":
                for(CDEvent event: cdEvents){
                    if (event.getSequence().equals(target.getSequence())){
                        detailEvents.add(event);
                    }
                }
        }
        return detailEvents;
    }
}
