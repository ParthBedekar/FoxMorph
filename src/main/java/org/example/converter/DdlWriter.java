package org.example.converter;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import org.example.model.ForeignKeyInfo;
import org.example.model.IndexInfo;
import org.example.model.SchemaInfo;
import org.example.util.ColumnMatcher;
import org.example.util.DbfTypeMapper;
import org.example.util.StringUtils;

import java.io.*;
import java.util.*;

/**
 * Writes CREATE TABLE, index, and FOREIGN KEY statements to the SQL output file.
 *
 * <p>{@code writeTable} now takes both the actual .dbf File (for reading columns)
 * and the catalog name (for looking up PKs/FKs in SchemaInfo), since they may
 * differ — e.g. file is EMPLOYEES.dbf but catalog entry is "employ".
 */
public class DdlWriter {

    private final File   dbcDir;
    private final File   sqlFile;
    private final String databaseName;

    public DdlWriter(File dbcDir, File sqlFile, String databaseName) {
        this.dbcDir       = dbcDir;
        this.sqlFile      = sqlFile;
        this.databaseName = databaseName;
    }

    public void writeDatabaseHeader() throws IOException {
        try (FileWriter w = new FileWriter(sqlFile, false)) {
            w.write("CREATE DATABASE IF NOT EXISTS `" + databaseName + "`;\n");
            w.write("USE `" + databaseName + "`;\n\n");
        }
    }

    /**
     * Writes DDL for one table.
     *
     * @param tableName   name to use in CREATE TABLE (actual file base name)
     * @param dbfFile     the actual .dbf file to read columns from
     * @param schema      parsed schema info
     * @param catalogName the name as it appears in the DBC catalog (for PK/FK lookup)
     */
    public String writeTable(String tableName, File dbfFile,
                             SchemaInfo schema, String catalogName) throws IOException {
        try (FileInputStream fis = new FileInputStream(dbfFile)) {
            DBFReader reader      = new DBFReader(fis);
            int       columnCount = reader.getFieldCount();
            if (columnCount == 0) return null;

            Set<String>         actualColumns = new LinkedHashSet<>();
            Map<String, String> columnMapping = new HashMap<>();
            List<String>        parts         = new ArrayList<>();

            for (int i = 0; i < columnCount; i++) {
                DBFField field   = reader.getField(i);
                String   colName = field.getName();
                if (colName == null || colName.isEmpty()) continue;
                actualColumns.add(colName.toUpperCase());
                columnMapping.put(colName.toLowerCase(), colName);
                parts.add("  `" + colName + "` " + DbfTypeMapper.toMySqlType(field));
            }

            // Look up PK/FK using catalog name first, fall back to actual table name
            String pkTag    = schema.primaryKeys.getOrDefault(catalogName,
                    schema.primaryKeys.get(tableName));
            String actualPk = resolveAndAddPk(pkTag, tableName, actualColumns, columnMapping, parts);
            addForeignKeys(catalogName, tableName, schema, actualColumns, columnMapping, parts);

            if (parts.isEmpty()) return null;

            try (FileWriter w = new FileWriter(sqlFile, true)) {
                w.write("DROP TABLE IF EXISTS `" + tableName + "`;\n");
                w.write("CREATE TABLE `" + tableName + "` (\n");
                w.write(String.join(",\n", parts));
                w.write("\n);\n");
                writeIndexes(w, catalogName, tableName, schema, actualColumns, columnMapping, actualPk);
                w.write(System.lineSeparator());
            }

            return actualPk;
        }
    }

    /**
     * Backwards-compatible overload for callers that don't need the catalog/actual split.
     */
    public String writeTable(String tableName, SchemaInfo schema) throws IOException {
        File dbfFile = new File(dbcDir, tableName + ".dbf");
        return writeTable(tableName, dbfFile, schema, tableName);
    }

    // -------------------------------------------------------------------------

    private String resolveAndAddPk(String pkTag, String tableName,
                                   Set<String> actualColumns,
                                   Map<String, String> columnMapping,
                                   List<String> parts) {
        if (pkTag == null || pkTag.isBlank()) return null;
        String matched = ColumnMatcher.match(pkTag, actualColumns, columnMapping);
        if (matched != null) {
            parts.add("  PRIMARY KEY (`" + matched + "`)");
        } else {
            System.out.printf("WARNING: Could not match PK tag '%s' in table '%s'%n", pkTag, tableName);
        }
        return matched;
    }

    private void addForeignKeys(String catalogName, String tableName,
                                SchemaInfo schema,
                                Set<String> actualColumns,
                                Map<String, String> columnMapping,
                                List<String> parts) {
        // Try catalog name first, then actual name
        List<ForeignKeyInfo> fkList = schema.foreignKeys.getOrDefault(catalogName,
                schema.foreignKeys.get(tableName));
        if (fkList == null) return;

        for (ForeignKeyInfo fk : fkList) {
            if (fk.column == null || fk.referencesTable == null) continue;
            String localCol = ColumnMatcher.match(fk.column, actualColumns, columnMapping);
            String refPk    = schema.primaryKeys.get(fk.referencesTable);
            if (localCol == null || refPk == null) continue;
            parts.add("  FOREIGN KEY (`" + localCol + "`) REFERENCES `"
                    + fk.referencesTable + "` (`" + refPk + "`)");
        }
    }

    private void writeIndexes(FileWriter w, String catalogName, String tableName,
                              SchemaInfo schema,
                              Set<String> actualColumns,
                              Map<String, String> columnMapping,
                              String pkColumn) throws IOException {
        List<IndexInfo> idxList = schema.indexes.getOrDefault(catalogName,
                schema.indexes.get(tableName));
        if (idxList == null) return;

        Set<String> written = new HashSet<>();
        for (IndexInfo idx : idxList) {
            String matched = ColumnMatcher.match(idx.column, actualColumns, columnMapping);
            if (matched == null || matched.equalsIgnoreCase(pkColumn)) continue;
            if (!written.add(matched.toUpperCase())) continue;

            String prefix    = idx.isUnique ? "uk_" : "idx_";
            String indexName = prefix + tableName + "_" + matched;
            if (indexName.length() > 64) indexName = indexName.substring(0, 64);

            w.write("CREATE " + (idx.isUnique ? "UNIQUE " : "")
                    + "INDEX `" + indexName + "` ON `" + tableName
                    + "` (`" + matched + "`);\n");
        }
    }
}
