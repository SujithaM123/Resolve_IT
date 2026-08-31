package com.resolveit.service;

import com.resolveit.entity.Incident;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class IncidentSimilarityService {

    private static final double SERVICE_WEIGHT = 0.20;
    private static final double CATEGORY_WEIGHT = 0.25;
    private static final double TEXT_WEIGHT = 0.40;
    private static final double SEVERITY_WEIGHT = 0.15;

    public double similarityPercent(Incident target, Incident candidate) {
        return similarityPercent(
                serviceNameOf(target),
                target.getCategory(),
                target.getTitle(),
                target.getDescription(),
                target.getSeverity(),
                candidate);
    }

    public double similarityPercent(String service,
                                    String category,
                                    String title,
                                    String description,
                                    String severity,
                                    Incident candidate) {

        double serviceScore = TextSimilarity.exactMatch(service, serviceNameOf(candidate));
        double categoryScore = TextSimilarity.exactMatch(category, candidate.getCategory());
        double severityScore = TextSimilarity.exactMatch(severity, candidate.getSeverity());

        Set<String> targetTokens = TextSimilarity.tokenize(title, description);
        Set<String> candidateTokens = TextSimilarity.tokenize(
                candidate.getTitle(),
                candidate.getDescription(),
                candidate.getRootCause(),
                candidate.getResolution());
        double textScore = TextSimilarity.jaccard(targetTokens, candidateTokens);

        double weighted = (serviceScore * SERVICE_WEIGHT)
                + (categoryScore * CATEGORY_WEIGHT)
                + (textScore * TEXT_WEIGHT)
                + (severityScore * SEVERITY_WEIGHT);

        return TextSimilarity.clampToPercent(weighted * 100.0);
    }

    private static String serviceNameOf(Incident incident) {
        return incident.getTeam() == null ? null : incident.getTeam().getServiceName();
    }
}
