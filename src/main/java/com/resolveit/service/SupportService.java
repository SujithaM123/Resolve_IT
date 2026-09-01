package com.resolveit.service;

import com.resolveit.dto.OpsAiResponse;
import com.resolveit.dto.SupportDashboardResponse.SupportAnalytics;
import com.resolveit.dto.SupportDashboardResponse;
import com.resolveit.dto.SupportDashboardResponse.SupportIncidentSummary;
import com.resolveit.dto.SupportIncidentUpdateRequest;
import com.resolveit.dto.SupportIncidentUpdateResponse;
import com.resolveit.dto.SupportDashboardResponse.SupportSummary;
import com.resolveit.exception.BadRequestException;
import com.resolveit.exception.ConflictException;
import com.resolveit.entity.Incident;
import com.resolveit.entity.Priority;
import com.resolveit.entity.IncidentStatus;
import com.resolveit.repository.IncidentAssignmentRepository;
import com.resolveit.repository.IncidentRepository;
import com.resolveit.security.AuthenticatedUser;
import com.resolveit.opsai.OpsAiAction;
import com.resolveit.opsai.OpsAiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportService {

    private static final int RECURRENCE_THRESHOLD = 2;

    private final IncidentRepository incidentRepository;
    private final IncidentAssignmentRepository assignmentRepository;
    private final IncidentAccessService accessService;
    private final IncidentService incidentService;
    private final OpsAiService opsAiService;
    private final RealtimeNotifier notifier;

    public SupportService(IncidentRepository incidentRepository,
                          IncidentAssignmentRepository assignmentRepository,
                          IncidentAccessService accessService,
                          IncidentService incidentService,
                          OpsAiService opsAiService,
                          RealtimeNotifier notifier) {
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessService = accessService;
        this.incidentService = incidentService;
        this.opsAiService = opsAiService;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public SupportDashboardResponse dashboard(AuthenticatedUser caller) {
        List<Incident> assigned = assignmentRepository.findCurrentIncidentsForSupportUser(caller.getUserId());

        List<Incident> resolved = assigned.stream()
                .filter(incident -> IncidentStatus.RESOLVED.stored().equalsIgnoreCase(incident.getStatus()))
                .toList();
        List<Incident> open = assigned.stream()
                .filter(incident -> !IncidentStatus.RESOLVED.stored().equalsIgnoreCase(incident.getStatus()))
                .toList();

        SupportSummary summary = new SupportSummary(
                assigned.size(),
                open.size(),
                resolved.size(),
                averageResolutionTime(resolved));

        List<SupportIncidentSummary> incidents = open.stream()
                .sorted(Comparator
                        .comparing((Incident incident) -> priorityRank(incident.getPriority()))
                        .thenComparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(incident -> new SupportIncidentSummary(
                        incident.getIncidentId(),
                        incident.getIncidentCode(),
                        incident.getTitle(),
                        incident.getSeverity(),
                        incident.getPriority(),
                        incident.getStatus()))
                .toList();

        return new SupportDashboardResponse(
                caller.getUserId(),
                caller.getName(),
                summary,
                incidents,
                analytics(assigned));
    }

    @Transactional
    public SupportIncidentUpdateResponse updateIncident(Long incidentId,
                                                        SupportIncidentUpdateRequest request,
                                                        AuthenticatedUser caller) {

        Incident incident = accessService.requireModifiable(incidentId, caller);

        IncidentStatus target = IncidentStatus.fromStored(request.status())
                .orElseThrow(() -> new BadRequestException(
                        "Status must be one of REPORTED, ASSIGNED, IN PROGRESS, "
                                + "ROOT CAUSE IDENTIFIED, RESOLUTION IN PROGRESS, RESOLVED"));

        IncidentStatus current = IncidentStatus.fromStored(incident.getStatus())
                .orElseThrow(() -> new ConflictException(
                        "Incident has an unrecognized current status and cannot be updated"));

        if (!current.canTransitionTo(target)) {
            throw new ConflictException(
                    "Invalid status transition from " + current.stored() + " to " + target.stored());
        }

        if (request.rootCause() != null && !request.rootCause().isBlank()) {
            incident.setRootCause(request.rootCause());
        }
        if (request.resolution() != null && !request.resolution().isBlank()) {
            incident.setResolution(request.resolution());
        }

        applyStatusRules(incident, target);

        LocalDateTime now = LocalDateTime.now();
        if (target == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(now);
        }
        incident.setStatus(target.stored());

        incidentService.writeLog(incident, target, now);
        Incident saved = incidentRepository.save(incident);

        return new SupportIncidentUpdateResponse(
                saved.getIncidentId(),
                saved.getIncidentCode(),
                saved.getStatus(),
                saved.getRootCause(),
                saved.getResolution(),
                saved.getResolvedAt());
    }

    public void broadcastIncidentUpdate(SupportIncidentUpdateResponse update) {
        notifier.broadcastIncidentUpdate(update.incidentId(), update);
    }

    @Transactional(readOnly = true)
    public OpsAiResponse assist(Long incidentId, String requestedAction, AuthenticatedUser caller) {

        OpsAiAction action = OpsAiAction.fromRequest(requestedAction)
                .orElseThrow(() -> new BadRequestException(
                        "Action must be one of SUMMARIZE, SIMILAR, ANALYZE, ROOT_CAUSE, RESOLUTION"));

        Incident incident = accessService.requireModifiable(incidentId, caller);
        return opsAiService.assist(incident, action);
    }

    private static void applyStatusRules(Incident incident, IncidentStatus target) {
        if (target == IncidentStatus.ROOT_CAUSE_IDENTIFIED && isBlank(incident.getRootCause())) {
            throw new ConflictException(
                    "Root cause must be confirmed before moving to ROOT CAUSE IDENTIFIED");
        }
        if (target == IncidentStatus.RESOLVED) {
            if (isBlank(incident.getRootCause())) {
                throw new ConflictException("Root cause must be recorded before resolving the incident");
            }
            if (isBlank(incident.getResolution())) {
                throw new ConflictException("Resolution details must be recorded before resolving the incident");
            }
        }
    }

    private static String averageResolutionTime(List<Incident> resolved) {
        List<Incident> timed = resolved.stream()
                .filter(incident -> incident.getCreatedAt() != null && incident.getResolvedAt() != null)
                .toList();

        if (timed.isEmpty()) {
            return "00:00:00";
        }

        long totalSeconds = timed.stream()
                .mapToLong(incident -> Duration.between(incident.getCreatedAt(), incident.getResolvedAt())
                        .getSeconds())
                .sum();

        Duration average = Duration.ofSeconds(totalSeconds / timed.size());
        return "%02d:%02d:%02d".formatted(average.toHours(), average.toMinutesPart(), average.toSecondsPart());
    }

    private static SupportAnalytics analytics(List<Incident> assigned) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Incident incident : assigned) {
            String category = incident.getCategory();
            if (category != null && !category.isBlank()) {
                byCategory.merge(category.trim(), 1L, Long::sum);
            }
        }

        String mostCommon = byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        long recurring = byCategory.values().stream()
                .filter(count -> count >= RECURRENCE_THRESHOLD)
                .count();

        return new SupportAnalytics(mostCommon, recurring);
    }

    private static int priorityRank(String priority) {
        return Priority.fromStored(priority)
                .map(Enum::ordinal)
                .orElse(Integer.MAX_VALUE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
