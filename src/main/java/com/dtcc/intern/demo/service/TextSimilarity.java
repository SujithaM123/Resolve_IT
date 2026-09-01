package com.dtcc.intern.demo.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TextSimilarity {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
            "to", "of", "in", "on", "at", "for", "with", "from", "by", "as", "it", "its", "this",
            "that", "these", "those", "i", "we", "you", "he", "she", "they", "not", "no", "so",
            "if", "then", "than", "when", "while", "after", "before", "during", "my", "our", "your",
            "have", "has", "had", "do", "does", "did", "can", "could", "will", "would", "should",
            "am", "there", "here", "get", "got", "getting", "please", "issue", "issues");

    private TextSimilarity() {
    }

    public static Set<String> tokenize(String... parts) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            Arrays.stream(part.toLowerCase().split("[^a-z0-9]+"))
                    .filter(token -> token.length() > 1)
                    .filter(token -> !STOP_WORDS.contains(token))
                    .forEach(tokens::add);
        }
        return tokens;
    }

    public static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    public static double exactMatch(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        return left.trim().equalsIgnoreCase(right.trim()) ? 1.0 : 0.0;
    }

    public static double clampToPercent(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }
}
