package com.dtcc.intern.demo.dto;

/**
 * Suggestion shown to the reporter before they submit an incident. Every field is
 * advisory - the reporter still chooses the real service on POST /api/incidents.
 *
 * When nothing in the text matches a known service, every suggestion field - service,
 * category AND severity - is null, matched is false, and message carries the sentence
 * the client should display. A zero-confidence result is never dressed up as a partial
 * suggestion: offering a severity for an incident we could not place would imply we
 * understood the report when we did not.
 */
public record ClassifyResponse(
        String suggestedService,
        String suggestedCategory,
        String suggestedSeverity,
        boolean matched,
        String message) {

    /** Convenience for a confident result. */
    public static ClassifyResponse matched(String service, String category, String severity) {
        return new ClassifyResponse(service, category, severity, true, null);
    }

    /** Convenience for "we could not tell" - the client asks the reporter to choose. */
    public static ClassifyResponse noMatch(String message) {
        return new ClassifyResponse(null, null, null, false, message);
    }
}
