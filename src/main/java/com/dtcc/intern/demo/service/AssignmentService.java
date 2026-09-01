package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.entity.AppUser;
import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.IncidentAssignment;
import com.dtcc.intern.demo.entity.Priority;
import com.dtcc.intern.demo.repository.AppUserRepository;
import com.dtcc.intern.demo.repository.IncidentAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private static final double EXPERIENCE_WEIGHT = 0.40;
    private static final double AVAILABILITY_WEIGHT = 0.25;
    private static final double WORKLOAD_WEIGHT = 0.20;
    private static final double FAIRNESS_WEIGHT = 0.15;

    private static final int BUSY_THRESHOLD = 5;

    private static final Duration MAX_IDLE = Duration.ofHours(4);

    private static final double AGE_WEIGHT_PER_DAY = 0.5;
    private static final double MAX_AGE_WEIGHT = 3.0;

    private final AppUserRepository appUserRepository;
    private final IncidentAssignmentRepository assignmentRepository;
    private final IncidentSimilarityService similarityService;

    public AssignmentService(AppUserRepository appUserRepository,
                             IncidentAssignmentRepository assignmentRepository,
                             IncidentSimilarityService similarityService) {
        this.appUserRepository = appUserRepository;
        this.assignmentRepository = assignmentRepository;
        this.similarityService = similarityService;
    }

    public record Candidate(AppUser engineer,
                            double experience,
                            double availability,
                            double workload,
                            double fairness,
                            double finalScore) {
    }

    public Optional<Candidate> selectEngineer(Incident incident) {
        Long teamId = incident.getTeam().getTeamId();
        List<AppUser> engineers = appUserRepository.findSupportEngineersByTeam(teamId);

        if (engineers.isEmpty()) {
            log.warn("No SUPPORT engineer belongs to team {}; incident stays unassigned", teamId);
            return Optional.empty();
        }

        List<EngineerState> states = engineers.stream().map(this::loadState).toList();

        List<EngineerState> eligible = states.stream()
                .filter(state -> state.availability().isEligible())
                .toList();
        if (eligible.isEmpty()) {
            log.warn("No eligible SUPPORT engineer for team {}; incident stays unassigned", teamId);
            return Optional.empty();
        }

        List<Double> rawExperiences = eligible.stream()
                .map(state -> rawExperience(state, incident))
                .toList();
        double maxExperience = rawExperiences.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double maxWorkload = eligible.stream().mapToDouble(EngineerState::workloadUnits).max().orElse(0.0);

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            EngineerState state = eligible.get(i);

            double experience = normalizeExperience(rawExperiences.get(i), maxExperience);
            double availability = state.availability().score();
            double workload = normalizeWorkload(state.workloadUnits(), maxWorkload);
            double fairness = fairnessScore(state.lastAssignedAt());

            double finalScore = (experience * EXPERIENCE_WEIGHT)
                    + (availability * AVAILABILITY_WEIGHT)
                    + (workload * WORKLOAD_WEIGHT)
                    + (fairness * FAIRNESS_WEIGHT);

            candidates.add(new Candidate(state.engineer(), experience, availability, workload, fairness,
                    TextSimilarity.clampToPercent(finalScore)));
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(Candidate::finalScore)
                        .thenComparing(candidate -> candidate.engineer().getUserId()));
    }

    public static BigDecimal toStoredScore(double score) {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private EngineerState loadState(AppUser engineer) {
        List<Incident> active = assignmentRepository.findActiveIncidentsForSupportUser(engineer.getUserId());
        List<Incident> handled = assignmentRepository.findCurrentIncidentsForSupportUser(engineer.getUserId());
        LocalDateTime lastAssignedAt = assignmentRepository
                .findTopBySupportUser_UserIdOrderByAssignedAtDescAssignmentIdDesc(engineer.getUserId())
                .map(IncidentAssignment::getAssignedAt)
                .orElse(null);

        return new EngineerState(engineer, handled, workloadUnits(active), availabilityOf(active), lastAssignedAt);
    }

    private static Availability availabilityOf(List<Incident> active) {
        return active.size() < BUSY_THRESHOLD ? Availability.AVAILABLE : Availability.BUSY;
    }

    private static double workloadUnits(List<Incident> active) {
        double units = 0.0;
        for (Incident incident : active) {
            double priorityWeight = Priority.fromStored(incident.getPriority())
                    .orElse(Priority.P4)
                    .workloadWeight();
            double ageWeight = 0.0;
            if (incident.getCreatedAt() != null) {
                long days = Duration.between(incident.getCreatedAt(), LocalDateTime.now()).toDays();
                ageWeight = Math.min(days * AGE_WEIGHT_PER_DAY, MAX_AGE_WEIGHT);
            }
            units += priorityWeight + ageWeight;
        }
        return units;
    }

    private double rawExperience(EngineerState state, Incident incident) {
        double total = 0.0;
        for (Incident handled : state.handledIncidents()) {
            if (handled.getIncidentId().equals(incident.getIncidentId())) {
                continue;
            }
            double similarity = similarityService.similarityPercent(incident, handled) / 100.0;

            double completionFactor = handled.getResolvedAt() != null ? 1.0 : 0.5;
            total += similarity * completionFactor;
        }
        return total;
    }

    private static double normalizeExperience(double raw, double max) {
        if (max <= 0.0) {

            return 50.0;
        }
        double normalized = (raw / max) * 100.0;
        return TextSimilarity.clampToPercent(40.0 + (normalized * 0.60));
    }

    private static double normalizeWorkload(double units, double maxUnits) {
        if (maxUnits <= 0.0) {
            return 100.0;
        }
        return TextSimilarity.clampToPercent(100.0 - ((units / maxUnits) * 100.0));
    }

    private static double fairnessScore(LocalDateTime lastAssignedAt) {
        if (lastAssignedAt == null) {
            return 100.0;
        }
        Duration idle = Duration.between(lastAssignedAt, LocalDateTime.now());
        if (idle.isNegative()) {
            return 0.0;
        }
        double ratio = (double) idle.toMillis() / MAX_IDLE.toMillis();
        return TextSimilarity.clampToPercent(ratio * 100.0);
    }

    private record EngineerState(AppUser engineer,
                                 List<Incident> handledIncidents,
                                 double workloadUnits,
                                 Availability availability,
                                 LocalDateTime lastAssignedAt) {
    }
}
