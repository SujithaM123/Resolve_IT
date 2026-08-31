package com.resolveit.service;

public enum Availability {

    AVAILABLE(100.0),
    BUSY(50.0),
    OFFLINE(0.0);

    private final double score;

    Availability(double score) {
        this.score = score;
    }

    public double score() {
        return score;
    }

    public boolean isEligible() {
        return this != OFFLINE;
    }
}
