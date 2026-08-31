package com.resolveit.dto;

import java.util.List;

public record SimilarIncidentsResult(List<SimilarIncident> similarIncidents) {

    public record SimilarIncident(String incidentCode, int similarity) {
    }
}
