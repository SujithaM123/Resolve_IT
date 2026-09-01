package com.resolveit.dto;

import java.util.List;

/**
 * The envelope every OpsAI action returns: which action ran, and its result.
 *
 * The five result shapes are nested here because each one is produced by exactly one
 * OpsAI action and is never returned on its own. Keeping them beside the response
 * that carries them means the whole OpsAI contract is readable in a single file.
 *
 * `result` stays Object because its shape depends on the action requested. The JSON
 * is unaffected by the nesting: Jackson serialises a nested record exactly as it
 * serialises a top-level one.
 */
public record OpsAiResponse(
        String action,
        Object result) {

    /** SUMMARIZE */
    public record SummarizeResult(String summary) {
    }

    /** SIMILAR */
    public record SimilarIncidentsResult(List<SimilarIncident> similarIncidents) {

        public record SimilarIncident(String incidentCode, int similarity) {
        }
    }

    /** ANALYZE */
    public record AnalyzeResult(
            String analysis,
            List<String> evidence) {
    }

    /** ROOT_CAUSE */
    public record RootCauseResult(
            String possibleRootCause,
            int confidence,
            List<String> evidence) {
    }

    /** RESOLUTION */
    public record ResolutionResult(List<String> recommendedSteps) {
    }
}
