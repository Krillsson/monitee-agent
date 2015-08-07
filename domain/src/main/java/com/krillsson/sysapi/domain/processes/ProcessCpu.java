package com.krillsson.sysapi.domain.processes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessCpu {
    double percent;
    long lastTime, startTime, user, sys, total;

    public ProcessCpu(double percent, long lastTime, long startTime, long user, long sys, long total) {
        this.percent = percent;
        this.lastTime = lastTime;
        this.startTime = startTime;
        this.user = user;
        this.sys = sys;
        this.total = total;
    }

    public ProcessCpu() {

    }

    @JsonProperty
    public double getPercent() {
        return percent;
    }

    @JsonProperty
    public long getLastTime() {
        return lastTime;
    }

    @JsonProperty
    public long getStartTime() {
        return startTime;
    }

    @JsonProperty
    public long getUser() {
        return user;
    }

    @JsonProperty
    public long getSys() {
        return sys;
    }

    @JsonProperty
    public long getTotal() {
        return total;
    }
}
