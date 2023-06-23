package com.ericsson.vici.entities;

import com.ericsson.vici.entities.Eiffel.CDEvent;

import java.util.ArrayList;

public class Flow {
    private ArrayList<CDEventData> cdEventData;
    private ArrayList<String> typeFlow;

    public Flow(){
    }

    public void addEvent(CDEventData data){
        cdEventData.add(data);
        typeFlow.add(data.getType());
    }
}
