package com.resolveit.service;

import com.resolveit.entity.Priority;
import com.resolveit.entity.Severity;
import org.springframework.stereotype.Service;

@Service
public class PriorityService {

    public Priority determinePriority(Severity severity) {
        if (severity == null) {
            return Priority.P4;
        }
        return switch (severity) {
            case CRITICAL, HIGH -> Priority.P1;
            case MEDIUM -> Priority.P3;
            case LOW -> Priority.P4;
        };
    }
}
