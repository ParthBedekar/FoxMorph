package org.example.converter;

import org.example.db.FptReader;
import org.example.model.ForeignKeyInfo;
import org.example.model.IndexInfo;
import org.example.model.SchemaInfo;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Parses a Visual FoxPro DBC catalog file via raw byte reading.
 *
 * No FPT dependency for constraint resolution — everything derived from
 * Index row tag names + Field row names.
 */
public class DbcCatalogReader {

    private static final Charset CP1252 = Charset.forName("Cp1252");

    private static final int OFF_OBJECTID   = 1;
    private static final int OFF_PARENTID   = 5;
    private static final int OFF_OBJECTTYPE = 9;
    private static final int OFF_OBJECTNAME = 19;
    private static final int OFF_PROPERTY   = 147;
    private static final int OFF_CODE       = 151;
    private static final int LEN_OBJECTTYPE = 10;
    private static final int LEN_OBJECTNAME = 128;

    private record CatalogRow(int objectId, int parentId, String objectType,
                              String objectName, int propPtr, int codePtr,
                              boolean deleted) {}

    public SchemaInfo read(String dbcPath) throws IOException {
        File dbcFile = new File(dbcPath);
        SchemaInfo schema = new SchemaInfo();
        List<CatalogRow> rows = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(dbcFile, "r")) {
            raf.seek(4); int numRecords = readIntLE(raf);
            raf.seek(8); int headerSize = readShortLE(raf);
            int recordSize = readShortLE(raf);
            System.out.printf("DBC: records=%d headerSize=%d recordSize=%d%n",
                    numRecords, headerSize, recordSize);
            byte[] rec = new byte[recordSize];
            for (int i = 0; i < numRecords; i++) {
                raf.seek((long) headerSize + (long) i * recordSize);
                if (raf.read(rec, 0, recordSize) < recordSize) break;
                rows.add(new CatalogRow(
                        readIntLE(rec, OFF_OBJECTID), readIntLE(rec, OFF_PARENTID),
                        readStr(rec, OFF_OBJECTTYPE, LEN_OBJECTTYPE),
                        readStr(rec, OFF_OBJECTNAME, LEN_OBJECTNAME),
                        readIntLE(rec, OFF_PROPERTY), readIntLE(rec, OFF_CODE),
                        rec[0] == '*'));
            }
        }

        // ── Pass 1: tables ────────────────────────────────────────────────────
        Map<Integer, String>       idToTable   = new LinkedHashMap<>();
        Map<Integer, List<String>> tableFields = new HashMap<>();

        for (CatalogRow row : rows) {
            if ("Table".equalsIgnoreCase(row.objectType()) && !row.objectName().isEmpty()) {
                idToTable.put(row.objectId(), row.objectName());
                schema.tables.add(row.objectName());
                System.out.println("Table" + (row.deleted() ? " (recovered)" : "")
                        + ": " + row.objectName() + " (id=" + row.objectId() + ")");
            }
        }

        // ── Pass 2: field names per table ─────────────────────────────────────
        for (CatalogRow row : rows) {
            if ("Field".equalsIgnoreCase(row.objectType()) && !row.objectName().isEmpty()) {
                if (idToTable.containsKey(row.parentId())) {
                    tableFields.computeIfAbsent(row.parentId(), _ -> new ArrayList<>())
                            .add(row.objectName().toLowerCase());
                }
            }
        }

        // ── Pass 3: Index rows → PKs, FKs, indexes ───────────────────────────
        // Store raw tag names — DdlWriter's ColumnMatcher will resolve to actual columns
        // For PKs: also try to resolve immediately so FK parent-table matching works
        for (CatalogRow row : rows) {
            if (row.deleted() || !"Index".equalsIgnoreCase(row.objectType())) continue;
            String table = idToTable.get(row.parentId());
            if (table == null) continue;

            String tagName  = row.objectName().trim();
            String tagLower = tagName.toLowerCase();
            if (tagName.isEmpty()) continue;

            // Try FPT first, fall back to field-name resolution, fall back to tag
            String expression = readFirstLine(dbcFile, row.propPtr());
            if (expression == null || expression.isEmpty() || expression.equalsIgnoreCase(tagName)) {
                expression = resolveFromFields(tagName, row.parentId(), tableFields);
            }
            if (expression == null || expression.isEmpty()) expression = tagName;

            System.out.printf("  Index: table=%-15s tag=%-20s expr=%s%n", table, tagName, expression);

            if (tagLower.startsWith("pk_") || tagLower.equals("pk")) {
                schema.primaryKeys.put(table, expression);
                System.out.println("    → PK: " + expression);

            } else if (tagLower.startsWith("fk_") || tagLower.startsWith("fk")) {
                // Store expression (resolved column) + tag as fallback
                // referencesTable resolved in pass 4
                schema.foreignKeys
                        .computeIfAbsent(table, _ -> new ArrayList<>())
                        .add(new ForeignKeyInfo(expression, null));
                System.out.println("    → FK col: " + expression);

            } else if (tagLower.startsWith("uk_") || tagLower.startsWith("uk")) {
                schema.indexes.computeIfAbsent(table, _ -> new ArrayList<>())
                        .add(new IndexInfo(tagName, expression, true));
                System.out.println("    → UNIQUE: " + expression);
            } else {
                schema.indexes.computeIfAbsent(table, _ -> new ArrayList<>())
                        .add(new IndexInfo(tagName, expression, false));
                System.out.println("    → index: " + expression);
            }
        }

        // ── Pass 4: resolve FK parent tables ─────────────────────────────────
        // Match each FK column against PKs of other tables
        for (Map.Entry<String, List<ForeignKeyInfo>> entry : schema.foreignKeys.entrySet()) {
            String childTable = entry.getKey();
            for (ForeignKeyInfo fk : entry.getValue()) {
                if (fk.referencesTable != null) continue;
                fk.referencesTable = resolveParentTable(childTable, fk.column, schema);
                System.out.printf("  FK resolved: %s.%s → %s%n",
                        childTable, fk.column, fk.referencesTable);
            }
        }

        // ── Pass 5: stored procedures ─────────────────────────────────────────
        for (CatalogRow row : rows) {
            if (row.deleted()) continue;
            if (!row.objectType().toUpperCase().startsWith("STOREDPROCEDURES")) continue;
            if (row.codePtr() <= 0) continue;
            String code = FptReader.read(dbcFile, row.codePtr());
            if (code != null && !code.isBlank()) {
                schema.procedures.add(code);
                System.out.println("Procedure: " + code.length() + " chars");
            }
        }

        System.out.printf("%nResult: %d tables | %d PKs | %d FK entries | %d procedures%n",
                schema.tables.size(), schema.primaryKeys.size(),
                schema.foreignKeys.values().stream().mapToInt(List::size).sum(),
                schema.procedures.size());
        schema.primaryKeys.forEach((t, pk) -> System.out.println("  PK: " + t + " → " + pk));
        schema.foreignKeys.forEach((t, fks) -> fks.forEach(fk ->
                System.out.println("  FK: " + t + "." + fk.column + " → " + fk.referencesTable)));

        return schema;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves FK parent table by finding a table (other than child) whose PK
     * column name matches the FK column name — with fuzzy matching.
     */
    private String resolveParentTable(String childTable, String fkColumn, SchemaInfo schema) {
        String fkNorm = normalize(fkColumn);

        // 1. Exact match against another table's PK
        for (Map.Entry<String, String> e : schema.primaryKeys.entrySet()) {
            if (e.getKey().equalsIgnoreCase(childTable)) continue;
            if (normalize(e.getValue()).equals(fkNorm)) return e.getKey();
        }

        // 2. FK column contains another table's PK (e.g. fk="categoryid", pk="categoryid")
        for (Map.Entry<String, String> e : schema.primaryKeys.entrySet()) {
            if (e.getKey().equalsIgnoreCase(childTable)) continue;
            String pkNorm = normalize(e.getValue());
            if (fkNorm.contains(pkNorm) || pkNorm.contains(fkNorm)) return e.getKey();
        }

        // 3. FK column name contains another table's name
        //    e.g. fkColumn="categoryid", table="categories" → "categor" common prefix
        for (String table : schema.tables) {
            if (table.equalsIgnoreCase(childTable)) continue;
            String tableNorm = normalize(table);
            // Remove trailing 's' for plural table names
            String tableSingular = tableNorm.endsWith("s")
                    ? tableNorm.substring(0, tableNorm.length() - 1) : tableNorm;
            if (fkNorm.startsWith(tableSingular) || fkNorm.startsWith(tableNorm)) {
                return table;
            }
        }

        return null;
    }

    /**
     * Resolves the actual column name from an index tag using Field rows.
     * e.g. tag "pk_catid" → strip "pk_" → "catid" → Field "categoryid" starts with "catid" → match
     */
    private String resolveFromFields(String tagName, int tableId,
                                     Map<Integer, List<String>> tableFields) {
        List<String> fields = tableFields.get(tableId);
        if (fields == null || fields.isEmpty()) return null;

        String stripped = tagName.toLowerCase()
                .replaceAll("^(pk_|fk_|uk_|pk|fk|uk)", "");
        if (stripped.isEmpty()) return null;

        // 1. Exact match
        for (String f : fields) if (f.equals(stripped)) return f;

        // 2. Field starts with stripped, or stripped starts with field
        for (String f : fields) {
            if (f.startsWith(stripped) || stripped.startsWith(f)) return f;
        }

        // 3. Best common prefix (min 3 chars)
        String best = null; int bestLen = 0;
        for (String f : fields) {
            int len = commonPrefixLen(f, stripped);
            if (len > bestLen && len >= 3) { bestLen = len; best = f; }
        }
        return best;
    }

    private String normalize(String s) {
        return s.replaceAll("(?i)^(pk_|fk_|uk_|idx_)", "")
                .replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private int commonPrefixLen(String a, String b) {
        int len = Math.min(a.length(), b.length());
        for (int i = 0; i < len; i++) if (a.charAt(i) != b.charAt(i)) return i;
        return len;
    }

    private String readFirstLine(File dbcFile, int ptr) {
        if (ptr <= 0) return null;
        try {
            String text = FptReader.read(dbcFile, ptr);
            if (text == null || text.isBlank()) return null;
            for (String line : text.split("[\r\n]+")) {
                String t = line.trim();
                if (!t.isEmpty()) return t;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int readIntLE(byte[] buf, int off) {
        return (buf[off] & 0xFF) | ((buf[off+1] & 0xFF) << 8)
                | ((buf[off+2] & 0xFF) << 16) | ((buf[off+3] & 0xFF) << 24);
    }

    private String readStr(byte[] buf, int off, int len) {
        int end = Math.min(off + len, buf.length);
        String s = new String(buf, off, end - off, CP1252);
        int nul = s.indexOf('\0');
        if (nul >= 0) s = s.substring(0, nul);
        return s.trim();
    }

    private int readIntLE(RandomAccessFile raf) throws IOException {
        return (raf.read() & 0xFF) | ((raf.read() & 0xFF) << 8)
                | ((raf.read() & 0xFF) << 16) | ((raf.read() & 0xFF) << 24);
    }

    private int readShortLE(RandomAccessFile raf) throws IOException {
        return (raf.read() & 0xFF) | ((raf.read() & 0xFF) << 8);
    }
}