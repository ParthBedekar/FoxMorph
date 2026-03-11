package org.example.util;

import java.util.Map;
import java.util.Set;

/**
 * Fuzzy-matches a catalog tag name (e.g. "PK_ORDERID_TAG") to an actual
 * column name present in the DBF file, using prefix stripping + Levenshtein.
 */
public final class ColumnMatcher {

    /** Maximum edit distance considered a valid match. */
    private static final int MAX_DISTANCE = 5;

    private ColumnMatcher() {}

    /**
     * @param tag            raw tag string from the catalog (e.g. "pk_customerid")
     * @param actualColumns  set of actual column names in UPPER CASE
     * @param columnMapping  lower-case column name → original-case column name
     * @return the matched column in original case, or {@code null} if no match found
     */
    public static String match(String tag,
                               Set<String> actualColumns,
                               Map<String, String> columnMapping) {
        if (tag == null || tag.isEmpty()) return null;

        String normalized = normalize(tag);

        String bestMatch = null;
        int bestScore = Integer.MAX_VALUE;

        for (String col : actualColumns) {
            String normalizedCol = normalize(col);

            // Exact match after normalization
            if (normalizedCol.equals(normalized)) {
                return columnMapping.get(col.toLowerCase());
            }

            // Prefix match — prefer shorter distance
            if (normalizedCol.startsWith(normalized) || normalized.startsWith(normalizedCol)) {
                int score = Math.abs(normalizedCol.length() - normalized.length());
                if (score < bestScore) {
                    bestScore = score;
                    bestMatch = col;
                }
                continue;
            }

            int dist = StringUtils.levenshtein(normalized, normalizedCol);
            if (dist < bestScore) {
                bestScore = dist;
                bestMatch = col;
            }
        }

        if (bestScore <= MAX_DISTANCE && bestMatch != null) {
            return columnMapping.get(bestMatch.toLowerCase());
        }
        return null;
    }

    /** Strips common index prefixes, trailing "tag", and non-alphanumeric chars. */
    private static String normalize(String s) {
        return s.replaceAll("(?i)^(pk_|fk_|ix_|uk_|idx_)", "")
                .replaceAll("(?i)_?tag$", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase();
    }
}
