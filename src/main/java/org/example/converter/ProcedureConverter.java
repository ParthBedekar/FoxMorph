package org.example.converter;

import java.io.*;
import java.util.List;

/**
 * Converts Visual FoxPro stored procedure source blocks to MySQL equivalents
 * and appends them to the SQL output file.
 */
public class ProcedureConverter {

    private final File sqlFile;

    public ProcedureConverter(File sqlFile) {
        this.sqlFile = sqlFile;
    }

    public void write(List<String> procedures) throws IOException {
        if (procedures.isEmpty()) return;

        try (FileWriter w = new FileWriter(sqlFile, true)) {
            w.write("\n-- ==========================================\n");
            w.write("-- Stored Procedures\n");
            w.write("-- ==========================================\n\n");

            for (String proc : procedures) {
                w.write(toMySql(proc));
                w.write("\n\n");
            }
        }

        System.out.printf("✓ Wrote %d stored procedure(s)%n", procedures.size());
    }

    // -------------------------------------------------------------------------
    // Conversion logic
    // -------------------------------------------------------------------------

    private String toMySql(String foxPro) {
        foxPro = foxPro.trim();

        int procStart = foxPro.toLowerCase().indexOf("procedure");
        if (procStart == -1) return "-- Could not parse procedure";

        int nameStart  = procStart + "procedure".length();
        int openParen  = foxPro.indexOf('(', nameStart);
        int nameEnd    = (openParen != -1) ? openParen : foxPro.indexOf('\n', nameStart);
        if (nameEnd == -1) nameEnd = foxPro.length();

        String procName = foxPro.substring(nameStart, nameEnd).trim();
        String paramList = "";
        int    bodyStart = nameEnd;

        if (openParen != -1) {
            int closeParen = foxPro.indexOf(')', openParen);
            if (closeParen != -1) {
                paramList = foxPro.substring(openParen + 1, closeParen).trim();
                bodyStart = closeParen + 1;
            }
        }

        String params = buildParamList(paramList);
        String body   = extractBody(foxPro, bodyStart);

        return "DELIMITER $$\n"
                + "DROP PROCEDURE IF EXISTS `" + procName + "` $$\n"
                + "CREATE PROCEDURE `" + procName + "` (" + params + ")\n"
                + "BEGIN\n"
                + indentBody(body)
                + "END $$\n"
                + "DELIMITER ;";
    }

    private String buildParamList(String raw) {
        if (raw.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        String[] params = raw.split(",");
        for (int i = 0; i < params.length; i++) {
            String p = params[i].trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append("IN ").append(p).append(" ").append(inferType(p));
        }
        return sb.toString();
    }

    /**
     * Infers a MySQL type from a FoxPro Hungarian-notation parameter prefix.
     * Defaults to VARCHAR(255) for unknown prefixes.
     */
    private String inferType(String paramName) {
        return switch (paramName.toLowerCase().substring(0, Math.min(2, paramName.length()))) {
            case "tc" -> "VARCHAR(255)";
            case "tn" -> "DECIMAL(10,2)";
            case "tl" -> "BOOLEAN";
            case "td" -> "DATE";
            case "tt" -> "DATETIME";
            case "ti" -> "INT";
            default   -> "VARCHAR(255)";
        };
    }

    private String extractBody(String foxPro, int bodyStart) {
        int endProc = foxPro.toLowerCase().lastIndexOf("endproc");
        if (endProc > bodyStart) {
            return convertSql(foxPro.substring(bodyStart, endProc).trim());
        }
        return "";
    }

    private String convertSql(String foxPro) {
        return foxPro
                .replaceAll("(?i)INSERT\\s+INTO\\s+([\\w]+)\\s*\\(", "INSERT INTO `$1` (")
                .replaceAll("(?i)VALUES\\s*\\(", "VALUES (")
                .replaceAll(";\\s*$", "");
    }

    private String indentBody(String body) {
        if (body.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            sb.append("    ").append(line.trim()).append("\n");
        }
        return sb.toString();
    }
}
