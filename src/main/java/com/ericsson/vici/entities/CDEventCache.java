package com.ericsson.vici.entities;

import com.ericsson.vici.api.entities.Preferences;

public class CDEventCache {
    private CDEvents events;
    private Preferences preferences;

    public CDEventCache(CDEvents cdEvents, Preferences preferences) {
        this.events = events;
        this.preferences = preferences;
    }

    public CDEvents getEvents() {
        return events;
    }

    public void setEvents(CDEvents events) {
        this.events = events;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }
}
