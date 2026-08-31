package com.resolveit.dto;

import java.util.List;

public record AnalyzeResult(
        String analysis,
        List<String> evidence) {
}
