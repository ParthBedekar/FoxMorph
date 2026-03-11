package org.example.util;

/**
 * General-purpose string utilities used across the converter pipeline.
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Right-pads {@code s} to exactly {@code width} characters, truncating if longer.
     */
    public static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return String.format("%-" + width + "s", s);
    }

    /**
     * Sanitizes a SQL identifier: replaces non-alphanumeric characters with underscores
     * and prepends an underscore when the name starts with a digit.
     */
    public static String sanitizeIdentifier(String s) {
        if (s == null || s.isEmpty()) return "converted_db";
        String t = s.replaceAll("[^A-Za-z0-9_$]", "_");
        return Character.isDigit(t.charAt(0)) ? "_" + t : t;
    }

    /**
     * Strips control characters (except CR/LF/TAB), trims, and returns {@code null}
     * when the result is empty or the literal string "null".
     */
    public static String normalizeRaw(Object value) {
        if (value == null) return null;
        String s = value.toString()
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .trim();
        return (s.isEmpty() || s.equalsIgnoreCase("null")) ? null : s;
    }

    /** Escapes backslashes and single quotes for use inside SQL string literals. */
    public static String escapeSql(String value) {
        return value.replace("\\", "\\\\").replace("'", "''").trim();
    }

    /**
     * Levenshtein distance between two strings.
     * Returns {@link Integer#MAX_VALUE} if either argument is null.
     */
    public static int levenshtein(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
