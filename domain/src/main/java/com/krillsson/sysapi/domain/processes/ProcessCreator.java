package com.krillsson.sysapi.domain.processes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessCreator {
    private String user;
    private String group;

    public ProcessCreator(String user, String group) {
        this.user = user;
        this.group = group;
    }

    public ProcessCreator() {
        this.user = "N/A";
        this.group = "N/A";
    }

    @JsonProperty
    public String getUser() {
        return user;
    }

    @JsonProperty
    public String getGroup() {
        return group;
    }
}
