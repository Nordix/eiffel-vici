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
package com.ericsson.vici.entities;

import com.ericsson.vici.entities.Eiffel.CDEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;

public class EventData {
    public static final String TRIGGERED = "Triggered";
    public static final String STARTED = "Started";
    public static final String FINISHED = "Finished";
    public static final String CANCELED = "Canceled";
    public static final String EXECUTION = "Execution";
    public static final String PACKAGED = "Packaged";
    public static final String PUBLISHED = "Published";
    public static final String DEPLOYED = "Deployed";

    public static final String REDIRECT = "REDIRECT";

    private CDEvent thisCDEvent;
    private HashMap<String, CDEvent> cdEvents;
    private HashMap<String, Long> times;
    private HashMap<String, Integer> quantities;
    private ArrayList<EventData> mergedEvents;

    private String id;
    private String type;
    private ArrayList<Link> links;
    private ArrayList<ChildLink> children;
    private String aggregateOn;

    public EventData(CDEvent cdEvent) {
        thisCDEvent = cdEvent;
        cdEvents = new HashMap<>();
        cdEvents.put(TRIGGERED, cdEvent);

        times = new HashMap<>();
        times.put(TRIGGERED, cdEvent.getTime());

        id = cdEvent.getId();
        type = cdEvent.getType();
        children = new ArrayList<>();

        aggregateOn = null;

        quantities = null;
        mergedEvents = null;

        links = cdEvent.getLinks();
    }

    public EventData(EventData event, String redirect) {
//        this.mergedEvents = event.getMergedEvents();
        this.id = event.getId();
        this.type = STARTED;
        this.aggregateOn = redirect;
        this.links = event.getLinks();
        this.thisCDEvent = event.getThisCDEvent();
        this.cdEvents = new HashMap<>();
        this.cdEvents.put(TRIGGERED, thisCDEvent);
        //times.put(TRIGGERED, thisCDEvent.getTime());
//        this.children = new ArrayList<>();

        this.times = event.getTimes();
    }

    public void increaseQuantity(String quantity) {
        if (quantities == null) {
            quantities = new HashMap<>();
        }
        if (quantities.containsKey(quantity)) {
            quantities.put(quantity, quantities.get(quantity) + 1);

        } else {
            quantities.put(quantity, 1);
        }
    }

    public void addEvent(EventData event) {
        if (mergedEvents == null) {
            mergedEvents = new ArrayList<>();
        }
        mergedEvents.add(event);
    }

    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CDEvent getThisCDEvent() {
        return thisCDEvent;
    }

    public void setThisCDEvent(CDEvent thisCDEvent) {
        this.thisCDEvent = thisCDEvent;
    }

    public HashMap<String, Long> getTimes() {
        return times;
    }

    public void setTimes(HashMap<String, Long> times) {
        this.times = times;
    }

    public HashMap<String, Integer> getQuantities() {
        return quantities;
    }

    public void setQuantities(HashMap<String, Integer> quantities) {
        this.quantities = quantities;
    }

    public ArrayList<EventData> getMergedEvents() {
        return mergedEvents;
    }

    public void setMergedEvents(ArrayList<EventData> mergedEvents) {
        this.mergedEvents = mergedEvents;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ArrayList<Link> getLinks() {
        return links;
    }

    public void setLinks(ArrayList<Link> links) {
        this.links = links;
    }

    public String getAggregateOn() {
        return aggregateOn;
    }

    public void setAggregateOn(String aggregateOn) {
        this.aggregateOn = aggregateOn;
    }

    public ArrayList<ChildLink> getChildren() {
        return children;
    }

    public void setChildren(ArrayList<ChildLink> children) {
        this.children = children;
    }

    public HashMap<String, CDEvent> getCDEvents() {
        return cdEvents;
    }

    public void setCDEvents(HashMap<String, CDEvent> cdEvents) {
        this.cdEvents = cdEvents;
    }
}
