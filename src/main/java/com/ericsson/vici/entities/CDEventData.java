package com.ericsson.vici.entities;

import com.ericsson.vici.entities.Eiffel.CDEvent;

import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;

public class CDEventData {

    private ArrayList<CDEvent> cdEvents = new ArrayList<CDEvent>();
    private String type;
    private Link target;
    private URI source;
    private long time;
    private String id;
    public CDEventData(CDEvent cdEvent){
        this.type = cdEvent.getType();
        this.source = cdEvent.getSource();
        this.cdEvents.add(cdEvent);
        if (cdEvent.getLinks().size() > 0)
            this.target = cdEvent.getLinks().get(0);
        if(cdEvent.getTime() !=null) {
            this.time = cdEvent.getTime();
        } else {
            this.time = System.currentTimeMillis();
        }

        this.id = cdEvent.getId();
    }

    public ArrayList<CDEvent> getCdEvents() {
        return cdEvents;
    }

    public void setCdEvents(ArrayList<CDEvent> cdEvents) {
        this.cdEvents = cdEvents;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Link getTarget() {
        return target;
    }

    public void setTarget(Link target) {
        this.target = target;
    }

    public URI getSource() {
        return source;
    }

    public void setSource(URI source) {
        this.source = source;
    }

    public void addEvent(CDEvent cdEvent){
        this.cdEvents.add(cdEvent);
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
