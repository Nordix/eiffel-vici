package com.ericsson.vici.entities.Eiffel;

import com.ericsson.vici.entities.Link;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;
import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CDEvent {
    private String id;
    private URI source;
    private String type;
    private Long time;
    private ArrayList<Link> links;

    private String Sequence;

    public CDEvent(String id, URI source, String type, Long time, ArrayList<Link> links) {
        this.id = id;
        this.source = source;
        this.type = type;
        this.time = time;
        this.links = links;
    }

    public CDEvent(){
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public URI getSource() {
        return source;
    }

    public void setSource(URI source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public ArrayList<Link> getLinks() {
        return links;
    }

    public void setLinks(ArrayList<Link> links) {
        this.links = links;
    }

    public String getSequence() {
        return Sequence;
    }

    public void setSequence(String sequence) {
        this.Sequence = sequence;
    }

    @Override
    public String toString() {
        return "CDEvent{" +
                "id='" + id + '\'' +
                ", source=" + source +
                ", type='" + type + '\'' +
                ", time=" + time +
                ", links=" + links +
                ", Sequence='" + Sequence + '\'' +
                '}';
    }
}
