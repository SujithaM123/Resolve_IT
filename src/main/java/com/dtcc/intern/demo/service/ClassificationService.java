package com.dtcc.intern.demo.service;

import com.dtcc.intern.demo.dto.ClassifyResponse;
import com.dtcc.intern.demo.entity.Incident;
import com.dtcc.intern.demo.entity.Severity;
import com.dtcc.intern.demo.entity.TeamService;
import com.dtcc.intern.demo.repository.IncidentRepository;
import com.dtcc.intern.demo.repository.TeamServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ClassificationService {

    private static final Map<Severity, List<String>> SEVERITY_KEYWORDS = new LinkedHashMap<>();

    private static final double RELEVANCE_FLOOR = 15.0;

    /**
     * Shown to the reporter when the text matches no known service, so the client can
     * ask them to pick one instead of presenting a guess as an answer.
     */
    public static final String NO_MATCH_MESSAGE =
            "No similar incident found. Please select a service manually.";

    /**
     * Words that appear in every team's own record ("Payment Service", "Identity Support
     * Team") and therefore say nothing about which team an incident belongs to. Without
     * this, a reporter writing "the payment service is down" would match all five teams
     * on the word "service" alone.
     */
    private static final Set<String> STRUCTURAL_TOKENS = Set.of(
            "service", "services", "team", "teams", "support", "supports", "handles", "handle",
            "department", "platform", "problems", "problem", "related", "and");

    static {
        SEVERITY_KEYWORDS.put(Severity.CRITICAL, List.of(
                "outage", "down", "unavailable", "data loss", "corrupted", "breach",
                "production down", "all users", "critical", "emergency", "crash"));
        SEVERITY_KEYWORDS.put(Severity.HIGH, List.of(
                "fail", "failing", "failure", "error", "cannot", "unable", "broken",
                "denied", "rejected", "declined", "blocked", "exception", "timeout"));
        SEVERITY_KEYWORDS.put(Severity.MEDIUM, List.of(
                "slow", "delay", "delayed", "latency", "intermittent", "sometimes",
                "occasionally", "degraded", "partial", "unstable", "dropping"));
        SEVERITY_KEYWORDS.put(Severity.LOW, List.of(
                "question", "clarification", "cosmetic", "typo", "minor", "request",
                "documentation", "suggestion"));
    }

    private final TeamServiceRepository teamServiceRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentSimilarityService similarityService;

    public ClassificationService(TeamServiceRepository teamServiceRepository,
                                 IncidentRepository incidentRepository,
                                 IncidentSimilarityService similarityService) {
        this.teamServiceRepository = teamServiceRepository;
        this.incidentRepository = incidentRepository;
        this.similarityService = similarityService;
    }

    @Transactional(readOnly = true)
    public ClassifyResponse classify(String title, String description) {
        TeamService team = matchService(title, description);
        if (team == null) {
            // Nothing in the text names a service we run. Return no suggestion at all -
            // not even a severity, which would imply we understood a report we did not.
            return ClassifyResponse.noMatch(NO_MATCH_MESSAGE);
        }

        List<Incident> history = incidentRepository.findAllNewestFirst();
        Incident closest = closestHistoricalIncident(title, description, history);

        return ClassifyResponse.matched(
                team.getServiceName(),
                suggestCategory(closest),
                suggestSeverity(title, description, closest));
    }

    private Incident closestHistoricalIncident(String title, String description, List<Incident> history) {
        Incident closest = null;
        double bestScore = 0.0;

        for (Incident candidate : history) {
            double score = similarityService
                    .similarityPercent(null, null, title, description, null, candidate);
            if (score > bestScore) {
                bestScore = score;
                closest = candidate;
            }
        }

        return bestScore >= RELEVANCE_FLOOR ? closest : null;
    }

    /**
     * Picks the team whose keywords the reporter's words actually hit.
     *
     * The team that shares the most words wins; ties go to the team where those words
     * make up more of what the reporter wrote. If no team shares a single keyword the
     * method returns null - it never falls back to "the first row in the table", which
     * is what used to route unrelated reports to Payment Service.
     */
    private TeamService matchService(String title, String description) {
        Set<String> incidentTokens = TextSimilarity.tokenize(title, description);
        if (incidentTokens.isEmpty()) {
            return null;
        }

        TeamService best = null;
        int bestOverlap = 0;
        double bestContainment = 0.0;

        for (TeamService team : teamServiceRepository.findAll()) {
            Set<String> teamTokens = new HashSet<>(TextSimilarity.tokenize(
                    team.getServiceName(), team.getTeamName(), team.getDepartment(), team.getDescription()));
            teamTokens.removeAll(STRUCTURAL_TOKENS);

            Set<String> shared = new HashSet<>(incidentTokens);
            shared.retainAll(teamTokens);

            int overlap = shared.size();
            if (overlap == 0) {
                continue;
            }
            double containment = (double) overlap / incidentTokens.size();

            boolean better = overlap > bestOverlap
                    || (overlap == bestOverlap && containment > bestContainment);
            if (better) {
                bestOverlap = overlap;
                bestContainment = containment;
                best = team;
            }
        }

        return best;
    }

    /**
     * Reuses the category of the closest past incident. When no past incident is close
     * enough there is nothing to reuse, so this returns null rather than echoing the
     * reporter's own title back at them as if it were a category.
     */
    private String suggestCategory(Incident closest) {
        if (closest != null && closest.getCategory() != null && !closest.getCategory().isBlank()) {
            return closest.getCategory();
        }
        return null;
    }

    private String suggestSeverity(String title, String description, Incident closest) {
        String haystack = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase();

        for (Map.Entry<Severity, List<String>> entry : SEVERITY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (haystack.contains(keyword)) {
                    return entry.getKey().name();
                }
            }
        }
        if (closest != null && closest.getSeverity() != null && !closest.getSeverity().isBlank()) {
            return closest.getSeverity();
        }
        return Severity.MEDIUM.name();
    }
}
