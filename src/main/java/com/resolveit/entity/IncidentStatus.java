package com.resolveit.entity;

import java.util.Arrays;
import java.util.Optional;

public enum IncidentStatus {

    REPORTED("REPORTED"),
    ASSIGNED("ASSIGNED"),
    IN_PROGRESS("IN PROGRESS"),
    ROOT_CAUSE_IDENTIFIED("ROOT CAUSE IDENTIFIED"),
    RESOLUTION_IN_PROGRESS("RESOLUTION IN PROGRESS"),
    RESOLVED("RESOLVED");

    private final String stored;

    IncidentStatus(String stored) {
        this.stored = stored;
    }

    public String stored() {
        return stored;
    }

    public static Optional<IncidentStatus> fromStored(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(s -> s.stored.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public boolean canTransitionTo(IncidentStatus target) {
        return target != null && target.ordinal() == this.ordinal() + 1;
    }
}
