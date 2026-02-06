package com.deskit.deskit.livehost.service;

public record VodStatsDelta(int viewDelta, int likeDelta, int reportDelta) {
    public boolean isEmpty() {
        return viewDelta == 0 && likeDelta == 0 && reportDelta == 0;
    }
}
