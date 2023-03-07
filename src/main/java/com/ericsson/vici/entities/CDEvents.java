package com.ericsson.vici.entities;

import java.util.ArrayList;
import java.util.HashMap;

public class CDEvents {
    private HashMap<String, EventData> events;
    private long timeCollected;
    private long timeStart;
    private long timeEnd;

    public CDEvents(HashMap<String, EventData> events, long timeStart, long timeEnd) {
        this.events = events;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.timeCollected = System.currentTimeMillis();
    }

    public CDEvents(HashMap<String, EventData> events, long timeStart, long timeEnd, long timeCollected) {
        this.events = events;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.timeCollected = timeCollected;
    }

    public HashMap<String, EventData> getEvents() {
        return events;
    }

    public void setEvents(HashMap<String, EventData> events) {
        this.events = events;
    }

    public long getTimeCollected() {
        return timeCollected;
    }

    public void setTimeCollected(long timeCollected) {
        this.timeCollected = timeCollected;
    }
}
