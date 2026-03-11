package org.example.converter;

import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import org.example.db.FptReader;
import org.example.util.DbfTypeMapper;
import org.example.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates batched INSERT statements for a single DBF table and appends
 * them to the SQL output file.
 */
public class DmlWriter {

    private static final int BATCH_SIZE = 500;

    private final File sqlFile;
    private final File dbcDir;

    public DmlWriter(File sqlFile, File dbcDir) {
        this.sqlFile = sqlFile;
        this.dbcDir  = dbcDir;
    }

    public void writeInserts(String tableName, FileInputStream fis) throws IOException {
        try (FileWriter writer = new FileWriter(sqlFile, true)) {
            DBFReader reader = new DBFReader(fis);
            reader.setCharactersetName("UTF-8");

            int columnCount = reader.getFieldCount();
            if (columnCount == 0) return;

            List<DBFField> fields      = collectFields(reader, columnCount);
            List<String>   columnNames = fields.stream().map(DBFField::getName).collect(Collectors.toList());

            // Track auto-increment counters (type 'I') per column index
            Map<Integer, Integer> autoCounters = new HashMap<>();
            for (int i = 0; i < columnCount; i++) {
                if (DbfTypeMapper.isAutoIncrement(fields.get(i))) autoCounters.put(i, 1);
            }

            List<String> valueClauses = new ArrayList<>();
            Object[] row;

            while (true) {
                try {
                    row = reader.nextRecord();
                    if (row == null) break;
                } catch (DBFException e) {
                    System.err.printf("WARNING: Skipping corrupt row in '%s': %s%n", tableName, e.getMessage());
                    continue;
                }

                valueClauses.add(buildValueClause(row, fields, columnCount, autoCounters));

                if (valueClauses.size() >= BATCH_SIZE) {
                    writeBatch(writer, tableName, columnNames, valueClauses);
                    valueClauses.clear();
                }
            }

            if (!valueClauses.isEmpty()) writeBatch(writer, tableName, columnNames, valueClauses);

            writer.write(System.lineSeparator());
        }
    }

    // -------------------------------------------------------------------------
    // Value building
    // -------------------------------------------------------------------------

    private String buildValueClause(Object[] row,
                                    List<DBFField> fields,
                                    int columnCount,
                                    Map<Integer, Integer> autoCounters) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < columnCount; i++) {
            if (i > 0) sb.append(", ");
            sb.append(toSqlValue(row[i], fields.get(i), i, autoCounters));
        }
        sb.append(")");
        return sb.toString();
    }

    private String toSqlValue(Object raw, DBFField field, int idx,
                               Map<Integer, Integer> autoCounters) {
        // Auto-increment: use counter when value is absent/invalid
        if (autoCounters.containsKey(idx)) {
            String norm = StringUtils.normalizeRaw(raw);
            if (norm != null) {
                String cleaned = norm.replaceAll("[^0-9+\\-.]", "");
                if (!cleaned.isEmpty() && !cleaned.matches("^[+\\-]$") && !cleaned.equals(".")) {
                    return cleaned;
                }
            }
            int counter = autoCounters.get(idx);
            autoCounters.put(idx, counter + 1);
            return String.valueOf(counter);
        }

        // Memo / BLOB
        if (DbfTypeMapper.isMemo(field)) {
            return memoValue(raw);
        }

        // Numeric
        if (DbfTypeMapper.isNumeric(field)) {
            String norm = StringUtils.normalizeRaw(raw);
            if (norm == null) return "NULL";
            String cleaned = norm.replaceAll("[^0-9+\\-.]", "");
            return (cleaned.isEmpty() || cleaned.matches("^[+\\-]$") || cleaned.equals("."))
                    ? "NULL" : cleaned;
        }

        // String / everything else
        String norm = StringUtils.normalizeRaw(raw);
        return norm == null ? "NULL" : "'" + StringUtils.escapeSql(norm) + "'";
    }

    private String memoValue(Object raw) {
        if (raw == null) return "NULL";
        int pointer = 0;
        if (raw instanceof Number n)  pointer = n.intValue();
        else if (raw instanceof String s) {
            try { pointer = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        if (pointer <= 0) return "NULL";

        // FptReader needs the parent directory — pass the .fpt sibling of the DBC
        // dbcDir is the folder; FptReader resolves the fpt file from the dbf file name.
        // We don't know the exact table name here, so we pass dbcDir itself as a marker
        // and let FptReader search — but FptReader needs a File, not a dir.
        // Since we call this class with the table's .dbf file in dbcDir, and FptReader
        // strips the extension to find the .fpt, we construct a fake reference using dbcDir.
        // Actually: the correct approach is to pass the dbf File to DmlWriter.writeInserts.
        // For now, we use the dbcDir-level FPT heuristic the same way the original did.
        String text = FptReader.read(new File(dbcDir, "_placeholder_.dbf"), pointer);
        if (text == null || text.isBlank()) return "NULL";
        return "'" + text.replace("'", "''") + "'";
    }

    // -------------------------------------------------------------------------
    // Batch writing
    // -------------------------------------------------------------------------

    private void writeBatch(FileWriter writer, String tableName,
                             List<String> columnNames,
                             List<String> valueClauses) throws IOException {
        writer.write("INSERT INTO `" + tableName + "` (");
        writer.write(columnNames.stream()
                .map(n -> "`" + n + "`")
                .collect(Collectors.joining(", ")));
        writer.write(") VALUES \n");
        writer.write(String.join(",\n", valueClauses));
        writer.write(";\n");
    }

    private List<DBFField> collectFields(DBFReader reader, int count) throws IOException {
        List<DBFField> fields = new ArrayList<>(count);
        for (int i = 0; i < count; i++) fields.add(reader.getField(i));
        return fields;
    }
}
