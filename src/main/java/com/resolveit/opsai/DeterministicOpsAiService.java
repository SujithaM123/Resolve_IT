package com.resolveit.opsai;

import com.resolveit.dto.AnalyzeResult;
import com.resolveit.dto.OpsAiResponse;
import com.resolveit.dto.ResolutionResult;
import com.resolveit.dto.RootCauseResult;
import com.resolveit.dto.SimilarIncidentsResult;
import com.resolveit.dto.SummarizeResult;
import com.resolveit.entity.Incident;
import com.resolveit.entity.IncidentMessage;
import com.resolveit.repository.IncidentMessageRepository;
import com.resolveit.repository.IncidentRepository;
import com.resolveit.service.IncidentSimilarityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeterministicOpsAiService implements OpsAiService {

    private static final int SIMILAR_LIMIT = 3;

    private static final double SIMILARITY_FLOOR = 20.0;

    private final IncidentRepository incidentRepository;
    private final IncidentMessageRepository messageRepository;
    private final IncidentSimilarityService similarityService;

    public DeterministicOpsAiService(IncidentRepository incidentRepository,
                                     IncidentMessageRepository messageRepository,
                                     IncidentSimilarityService similarityService) {
        this.incidentRepository = incidentRepository;
        this.messageRepository = messageRepository;
        this.similarityService = similarityService;
    }

    @Override
    @Transactional(readOnly = true)
    public OpsAiResponse assist(Incident incident, OpsAiAction action) {
        return switch (action) {
            case SUMMARIZE -> new OpsAiResponse(action.name(), summarize(incident));
            case SIMILAR -> new OpsAiResponse(action.name(), similar(incident));
            case ANALYZE -> new OpsAiResponse(action.name(), analyze(incident));
            case ROOT_CAUSE -> new OpsAiResponse(action.name(), rootCause(incident));
            case RESOLUTION -> new OpsAiResponse(action.name(), resolution(incident));
        };
    }

    private SummarizeResult summarize(Incident incident) {
        List<IncidentMessage> messages = conversation(incident);

        StringBuilder summary = new StringBuilder();
        summary.append("User reported '").append(incident.getTitle()).append("'");
        if (incident.getTeam() != null) {
            summary.append(" against ").append(incident.getTeam().getServiceName());
        }
        summary.append(" with ").append(nullSafe(incident.getSeverity(), "unspecified"))
                .append(" severity and priority ").append(nullSafe(incident.getPriority(), "unassigned"))
                .append(". ");

        if (messages.isEmpty()) {
            summary.append("No conversation has taken place yet. ");
        } else {
            long userMessages = messages.stream().filter(m -> isRole(m, "USER")).count();
            long supportMessages = messages.size() - userMessages;

            summary.append("The conversation holds ").append(messages.size())
                    .append(" messages (").append(userMessages).append(" from the user, ")
                    .append(supportMessages).append(" from support). ");

            IncidentMessage first = messages.get(0);
            IncidentMessage last = messages.get(messages.size() - 1);
            summary.append("It opens with: \"").append(condense(first.getMessageText())).append("\". ");
            if (messages.size() > 1) {
                summary.append("The latest update is: \"").append(condense(last.getMessageText())).append("\". ");
            }
        }

        summary.append("The incident is currently ").append(nullSafe(incident.getStatus(), "unknown")).append(".");

        if (incident.getRootCause() != null && !incident.getRootCause().isBlank()) {
            summary.append(" Confirmed root cause: ").append(condense(incident.getRootCause())).append(".");
        }
        if (incident.getResolution() != null && !incident.getResolution().isBlank()) {
            summary.append(" Resolution: ").append(condense(incident.getResolution())).append(".");
        }

        return new SummarizeResult(summary.toString());
    }

    private SimilarIncidentsResult similar(Incident incident) {
        List<SimilarIncidentsResult.SimilarIncident> matches = rankedHistory(incident).stream()
                .map(scored -> new SimilarIncidentsResult.SimilarIncident(
                        scored.incident().getIncidentCode(),
                        (int) Math.round(scored.similarity())))
                .limit(SIMILAR_LIMIT)
                .toList();

        return new SimilarIncidentsResult(matches);
    }

    private AnalyzeResult analyze(Incident incident) {
        List<IncidentMessage> messages = conversation(incident);
        List<ScoredIncident> history = rankedHistory(incident);

        StringBuilder analysis = new StringBuilder();
        analysis.append("Incident '").append(incident.getTitle()).append("' is categorized as ")
                .append(nullSafe(incident.getCategory(), "uncategorized"));
        if (incident.getTeam() != null) {
            analysis.append(" on ").append(incident.getTeam().getServiceName());
        }
        analysis.append(", at ").append(nullSafe(incident.getSeverity(), "unspecified"))
                .append(" severity. ");

        if (history.isEmpty()) {
            analysis.append("No comparable historical incident was found, so this appears to be a new problem. ");
        } else {
            ScoredIncident closest = history.get(0);
            analysis.append("The closest historical match is ").append(closest.incident().getIncidentCode())
                    .append(" at ").append(Math.round(closest.similarity())).append("% similarity");

            long recurring = history.stream()
                    .filter(scored -> scored.similarity() >= SIMILARITY_FLOOR)
                    .count();
            if (recurring >= 2) {
                analysis.append(", and ").append(recurring)
                        .append(" historical incidents share these characteristics, "
                                + "which suggests a recurring operational problem");
            }
            analysis.append(". ");
        }

        analysis.append(messages.isEmpty()
                ? "No conversation evidence is available yet."
                : "The conversation contains " + messages.size() + " messages to review.");

        List<String> evidence = new ArrayList<>();
        evidence.add("Current incident description");
        if (!messages.isEmpty()) {
            evidence.add("Incident conversation (" + messages.size() + " messages)");
        }
        if (!history.isEmpty()) {
            evidence.add("Historical similar incidents (" + history.size() + " candidates examined)");
        }
        history.stream()
                .filter(scored -> scored.incident().getRootCause() != null
                        && !scored.incident().getRootCause().isBlank())
                .limit(SIMILAR_LIMIT)
                .forEach(scored -> evidence.add(
                        scored.incident().getIncidentCode() + " root cause: "
                                + condense(scored.incident().getRootCause())));

        return new AnalyzeResult(analysis.toString(), evidence);
    }

    private RootCauseResult rootCause(Incident incident) {
        List<ScoredIncident> history = rankedHistory(incident).stream()
                .filter(scored -> scored.similarity() >= SIMILARITY_FLOOR)
                .filter(scored -> scored.incident().getRootCause() != null
                        && !scored.incident().getRootCause().isBlank())
                .toList();

        if (history.isEmpty()) {
            return new RootCauseResult(
                    null,
                    0,
                    List.of("No historical incident with a confirmed root cause resembles this incident"));
        }
        Map<String, List<ScoredIncident>> byRootCause = new LinkedHashMap<>();
        for (ScoredIncident scored : history) {
            byRootCause.computeIfAbsent(scored.incident().getRootCause().trim(), key -> new ArrayList<>())
                    .add(scored);
        }

        Map.Entry<String, List<ScoredIncident>> best = byRootCause.entrySet().stream()
                .max(Comparator.comparingDouble(entry -> entry.getValue().stream()
                        .mapToDouble(ScoredIncident::similarity)
                        .sum()))
                .orElseThrow();

        List<ScoredIncident> supporting = best.getValue();
        double averageSimilarity = supporting.stream()
                .mapToDouble(ScoredIncident::similarity)
                .average()
                .orElse(0.0);
        double agreement = (double) supporting.size() / history.size();
        int confidence = (int) Math.round(Math.min(99.0, (averageSimilarity * 0.7) + (agreement * 30.0)));

        List<String> evidence = new ArrayList<>();
        evidence.add(supporting.size() + " similar historical incident"
                + (supporting.size() == 1 ? " had" : "s had") + " the same root cause");
        supporting.stream()
                .limit(SIMILAR_LIMIT)
                .forEach(scored -> evidence.add(scored.incident().getIncidentCode()
                        + " at " + Math.round(scored.similarity()) + "% similarity"));

        return new RootCauseResult(best.getKey(), confidence, evidence);
    }
    private ResolutionResult resolution(Incident incident) {
        List<String> steps = rankedHistory(incident).stream()
                .filter(scored -> scored.similarity() >= SIMILARITY_FLOOR)
                .map(scored -> scored.incident().getResolution())
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> splitIntoSteps(value).stream())
                .distinct()
                .limit(6)
                .toList();

        if (!steps.isEmpty()) {
            return new ResolutionResult(steps);
        }
        String service = incident.getTeam() == null ? "the affected service" : incident.getTeam().getServiceName();
        String category = nullSafe(incident.getCategory(), "the reported problem");

        return new ResolutionResult(List.of(
                "Reproduce the reported behaviour for " + category,
                "Review recent changes and deployments for " + service,
                "Check " + service + " logs and error rates around the reported time",
                "Check dependent systems and connection health",
                "Confirm the fix with the user and record the resolution"));
    }
    private List<ScoredIncident> rankedHistory(Incident incident) {
        return incidentRepository.findHistoricalExcluding(incident.getIncidentId()).stream()
                .map(candidate -> new ScoredIncident(
                        candidate, similarityService.similarityPercent(incident, candidate)))
                .filter(scored -> scored.similarity() > 0.0)
                .sorted(Comparator.comparingDouble(ScoredIncident::similarity).reversed())
                .toList();
    }

    private List<IncidentMessage> conversation(Incident incident) {
        return messageRepository.findByIncident_IncidentIdOrderBySentAtAscMessageIdAsc(incident.getIncidentId());
    }

    private static List<String> splitIntoSteps(String resolution) {
        return Arrays.stream(resolution.split("[.;\\n]"))
                .map(String::trim)
                .filter(step -> step.length() > 8)
                .map(DeterministicOpsAiService::condense)
                .toList();
    }

    private static boolean isRole(IncidentMessage message, String role) {
        return message.getSender() != null
                && message.getSender().getRole() != null
                && role.equalsIgnoreCase(message.getSender().getRole().getRoleName().trim());
    }

    private static String condense(String text) {
        if (text == null) {
            return "";
        }
        String flattened = text.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 160 ? flattened : flattened.substring(0, 157).trim() + "...";
    }

    private static String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ScoredIncident(Incident incident, double similarity) {
    }
}
