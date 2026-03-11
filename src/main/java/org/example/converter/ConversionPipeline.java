package org.example.converter;

import org.example.config.AppConfig;
import org.example.db.SqlExecutor;
import org.example.model.SchemaInfo;
import org.example.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

public class ConversionPipeline {

    private final AppConfig        config;
    private final Consumer<String> log;

    public ConversionPipeline(AppConfig config) {
        this(config, System.out::println);
    }

    public ConversionPipeline(AppConfig config, Consumer<String> log) {
        this.config = config;
        this.log    = log;
    }

    /** Full run: generate SQL then execute against MySQL. */
    public void run(String dbcPath, String destDir, String filename) throws Exception {
        File sqlFile = generate(dbcPath, destDir, filename);
        log.accept("🚀 Executing SQL against MySQL...");
        new SqlExecutor(config).execute(sqlFile);
    }

    /**
     * Generate-only mode: writes the SQL file and returns it.
     * Does NOT execute against MySQL.
     */
    public File generate(String dbcPath, String destDir, String filename) throws Exception {
        File dbcFile = new File(dbcPath);
        File dbcDir  = dbcFile.getParentFile();
        File outDir  = new File(destDir);
        outDir.mkdirs();

        File   sqlFile = new File(outDir, filename);
        String rawDb   = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        String dbName  = StringUtils.sanitizeIdentifier(rawDb);

        Map<String, File> dbfIndex = buildDbfIndex(dbcDir);
        log.accept("   DBF files in folder: " + dbfIndex.keySet());

        log.accept("📂 Reading catalog: " + dbcFile.getName());
        SchemaInfo schema = new DbcCatalogReader().read(dbcPath);
        log.accept(String.format("   Found %d table(s), %d procedure(s)",
                schema.tables.size(), schema.procedures.size()));

        DdlWriter    ddl     = new DdlWriter(dbcDir, sqlFile, dbName);
        DmlWriter    dml     = new DmlWriter(sqlFile, dbcDir);
        List<String> skipped = new ArrayList<>();
        int          done    = 0;

        ddl.writeDatabaseHeader();

        for (String table : schema.tables) {
            if (table == null || table.isBlank()) continue;

            File dbfFile = resolveDbf(table, dbcDir, dbfIndex);
            if (dbfFile == null) {
                log.accept("⚠️  Skipping (no matching .dbf): " + table);
                skipped.add(table + " — no .dbf file found");
                continue;
            }

            String actualTableName = stripExt(dbfFile.getName());
            if (!actualTableName.equalsIgnoreCase(table)) {
                log.accept("   ↳ Resolved '" + table + "' → " + dbfFile.getName());
            }

            log.accept("⚙️  Processing table: " + actualTableName);
            try {
                ddl.writeTable(actualTableName, dbfFile, schema, table);
            } catch (Exception e) {
                String msg = actualTableName + " — DDL failed: " + e.getMessage();
                log.accept("❌ " + msg);
                skipped.add(msg);
                continue;
            }

            try (FileInputStream fis = new FileInputStream(dbfFile)) {
                dml.writeInserts(actualTableName, fis);
            } catch (Exception e) {
                String msg = actualTableName + " — INSERT failed: " + e.getMessage();
                log.accept("❌ " + msg);
                skipped.add(msg);
                continue;
            }

            done++;
            log.accept("   ✓ " + actualTableName);
        }

        if (!schema.procedures.isEmpty()) {
            log.accept("📝 Writing " + schema.procedures.size() + " stored procedure(s)...");
            new ProcedureConverter(sqlFile).write(schema.procedures);
        }

        log.accept("─".repeat(48));
        log.accept(String.format("✅ Done — %d table(s) converted successfully.", done));
        if (!skipped.isEmpty()) {
            log.accept("⚠️  " + skipped.size() + " table(s) had errors:");
            skipped.forEach(s -> log.accept("     • " + s));
        }
        log.accept("📄 Output: " + sqlFile.getAbsolutePath());

        return sqlFile;
    }

    /** Execute a previously generated SQL file against MySQL. */
    public void execute(File sqlFile) throws Exception {
        log.accept("🚀 Executing SQL against MySQL...");
        new SqlExecutor(config).execute(sqlFile);
        log.accept("✅ Execution complete.");
    }

    // ── DBF helpers ───────────────────────────────────────────────────────────

    private Map<String, File> buildDbfIndex(File dir) {
        Map<String, File> index = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".dbf"));
        if (files != null) {
            for (File f : files) index.put(stripExt(f.getName()).toLowerCase(), f);
        }
        return index;
    }

    private File resolveDbf(String catalogName, File dbcDir, Map<String, File> index) {
        String lower = catalogName.toLowerCase();
        if (index.containsKey(lower)) return index.get(lower);
        for (Map.Entry<String, File> e : index.entrySet()) {
            if (e.getKey().startsWith(lower)) return e.getValue();
        }
        for (Map.Entry<String, File> e : index.entrySet()) {
            if (lower.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }
}