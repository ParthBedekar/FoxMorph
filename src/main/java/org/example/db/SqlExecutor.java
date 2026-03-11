package org.example.db;

import org.example.config.AppConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.stream.Collectors;

/**
 * Reads a SQL file and executes it against MySQL in batches.
 */
public final class SqlExecutor {

    private static final int BATCH_SIZE = 100;

    private final AppConfig config;

    public SqlExecutor(AppConfig config) {
        this.config = config;
    }

    public void execute(File sqlFile) throws SQLException, IOException {
        String sql = readFile(sqlFile);
        String[] statements = sql.split(";");

        try (Connection conn = DriverManager.getConnection(
                config.getMysqlUrl(), config.getMysqlUser(), config.getMysqlPassword());
             Statement stmt = conn.createStatement()) {

            int batched = 0;
            int executed = 0;

            for (String raw : statements) {
                String query = raw.trim();
                if (query.isEmpty()) continue;

                stmt.addBatch(query);
                if (++batched >= BATCH_SIZE) {
                    executed += stmt.executeBatch().length;
                    batched = 0;
                }
            }
            if (batched > 0) {
                executed += stmt.executeBatch().length;
            }

            System.out.printf("✓ Executed %d SQL statements%n", executed);
        }
    }

    private static String readFile(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }
}
