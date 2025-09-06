package foxql;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;


public class Converter {
    private final String destinationPath;
    private final String filename;
    private File dbcDir;

    public static class ForeignKeyInfo {
        String column;
        String referencesTable;

        public ForeignKeyInfo(String column, String referencesTable) {
            this.column = column;
            this.referencesTable = referencesTable;
        }
    }

    Converter(String dest, String name) {
        this.destinationPath = dest;
        this.filename = name;
    }

    public void read(String path, File ignoredOutFile) {
        dbcDir = new File(path).getParentFile();
        if (dbcDir == null || !dbcDir.isDirectory()) return;

        HashMap<String, String> primaryKeys = new HashMap<>();
        HashMap<String, ArrayList<ForeignKeyInfo>> foreignKeys = new HashMap<>();
        HashMap<Integer, String> tablesIndex = new HashMap<>();
        ArrayList<String> tablesList = new ArrayList<>();
        String currentTable = null;
        int tableCounter = 1;

        String[] headers = {"OBJECTID", "PARENTID", "OBJECTTYPE", "OBJECTNAME", "PROPERTY", "CODE", "RIINFO", "USER"};
        int[] colWidths = {10, 10, 30, 30, 10, 10, 10, 10};

        try (FileInputStream fis = new FileInputStream(new File(path))) {
            DBFReader reader = new DBFReader(fis);
            reader.setCharactersetName("Cp1252");

            // Print header line
            printTableHeader(headers, colWidths);
            String currentField = null;

            HashMap<String, String> fieldDefaultValues = new HashMap<>();
            HashMap<String, Boolean> fieldUniqueFlags = new HashMap<>();

            Object[] row;
            while ((row = reader.nextRecord()) != null) {

                String[] cells = new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    if (i < row.length && row[i] != null) {
                        String val = row[i].toString()
                                .replaceAll("\\p{Cntrl}", " ")
                                .replaceAll("[^\\x20-\\x7E]", "")
                                .replaceAll("\\s+", " ")
                                .trim();
                        if (val.equalsIgnoreCase("null") || val.isEmpty()) {
                            cells[i] = "";
                        } else {
                            cells[i] = val;
                        }


                        if (cells[i].toLowerCase().startsWith("table ")) {
                            currentTable = cells[i].replaceAll("(?i)table\\s+(\\w+)", "$1");
                            if (currentTable != null && !currentTable.isEmpty()) {
                                tablesIndex.put(tableCounter++, currentTable);
                                if (!tablesList.contains(currentTable)) tablesList.add(currentTable);
                            }
                        }
                        if (currentTable != null) {
                            if (cells[i].matches(".*\\bpk_\\w+.*")) {
                                String pkCol = cells[i].replaceAll(".*\\bpk_(\\w+).*", "$1");
                                if (pkCol != null && !pkCol.isEmpty()) primaryKeys.put(currentTable, pkCol);
                            } else if (cells[i].toLowerCase().startsWith("index ")) {
                                if (!primaryKeys.containsKey(currentTable)) {
                                    String idxCol = cells[i].replaceAll("(?i)index\\s+(\\w+)", "$1");
                                    if (idxCol != null && !idxCol.isEmpty()) {
                                        primaryKeys.put(currentTable, idxCol);
                                    }
                                }
                            } else if (cells[i].toLowerCase().startsWith("dex fk")) {
                                String fkName = "";
                                if ((i + 1) < row.length && row[i + 1] != null) {
                                    fkName = row[i + 1].toString().replaceFirst("_", "").trim();
                                }
                                if (!fkName.isEmpty()) {
                                    foreignKeys.computeIfAbsent(currentTable, k -> new ArrayList<>())
                                            .add(new ForeignKeyInfo(fkName, null));
                                }
                            } else if (cells[i].toLowerCase().startsWith("relation ")) {
                                String relationNumStr = cells[i].toLowerCase().replace("relation", "").trim();
                                try {
                                    int relationNum = Integer.parseInt(relationNumStr);
                                    if (tablesIndex.containsKey(relationNum)) {
                                        String referencedTable = tablesIndex.get(relationNum);
                                        ArrayList<ForeignKeyInfo> fkList = foreignKeys.get(currentTable);
                                        if (fkList != null && !fkList.isEmpty()) {
                                            fkList.get(fkList.size() - 1).referencesTable = referencedTable;
                                        }
                                    }
                                } catch (NumberFormatException ignore) {
                                }
                            }
                        }
                    } else {
                        cells[i] = "";
                    }
                }

                printTableRow(cells, colWidths);
            }

            System.out.println();
            System.out.println("=== Primary Keys ===");
            for (String table : primaryKeys.keySet()) {
                System.out.println(table + " : " + primaryKeys.get(table));
            }
            System.out.println();
            System.out.println("=== Foreign Keys ===");
            for (String table : foreignKeys.keySet()) {
                String fks = foreignKeys.get(table).stream()
                        .map(f -> f.column + "->" + f.referencesTable)
                        .collect(Collectors.joining(", "));
                System.out.println(table + " : " + fks);
            }
            System.out.println();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printTableHeader(String[] headers, int[] colWidths) {
        for (int i = 0; i < headers.length; i++) {
            System.out.print(padRight(headers[i], colWidths[i]));
            if (i < headers.length - 1) System.out.print("   ");
        }
        System.out.println();
        // Print a line separator
        int totalWidth = 0;
        for (int w : colWidths) totalWidth += w + 3;
        totalWidth -= 3; // last col no spaces
        for (int i = 0; i < totalWidth; i++) System.out.print("-");
        System.out.println();
    }

    private void printTableRow(String[] columns, int[] colWidths) {
        for (int i = 0; i < columns.length; i++) {
            System.out.print(padRight(columns[i], colWidths[i]));
            if (i < columns.length - 1) System.out.print("   ");
        }
        System.out.println();
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    public void dbf(ArrayList<String> tables, HashMap<String, String> pks, HashMap<String, ArrayList<ForeignKeyInfo>> fks) {
        if (dbcDir == null) return;

        File destDir = new File(destinationPath);
        if (!destDir.exists()) destDir.mkdirs();

        File sqlFile = new File(destDir, filename);
        String rawDb = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        String db = sanitizeIdentifier(rawDb);

        try (FileWriter writer = new FileWriter(sqlFile, false)) {
            writer.write("CREATE DATABASE IF NOT EXISTS `" + db + "`;\n");
            writer.write("USE `" + db + "`;\n\n");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (String tableName : tables) {
            if (tableName == null || tableName.isEmpty()) continue;

            File dbfPath = new File(dbcDir, tableName + ".dbf");
            if (!dbfPath.isFile()) continue;

            try (FileInputStream dbfFile = new FileInputStream(dbfPath);
                 FileWriter writer = new FileWriter(sqlFile, true)) {

                DBFReader reader = new DBFReader(dbfFile);
                int columns = reader.getFieldCount();
                if (columns <= 0) continue;

                ArrayList<String> parts = new ArrayList<>();
                for (int i = 0; i < columns; i++) {
                    DBFField field = reader.getField(i);
                    String colName = field.getName();
                    if (colName == null || colName.isEmpty()) continue;
                    parts.add("  `" + colName + "` " + datatypeUpdater(field));
                }

                String pkCol = pks.get(tableName);
                if (pkCol != null && !pkCol.isEmpty()) {
                    parts.add("  PRIMARY KEY (`" + pkCol + "`)");
                }

                if (fks.containsKey(tableName)) {
                    for (ForeignKeyInfo fk : fks.get(tableName)) {
                        if (fk == null) continue;
                        String fkCol = fk.column;
                        String refTable = fk.referencesTable;
                        if (fkCol == null || fkCol.isEmpty()) continue;
                        if (refTable == null || refTable.isEmpty()) continue;
                        String refPk = pks.get(refTable);
                        if (refPk == null || refPk.isEmpty()) continue;
                        parts.add("  FOREIGN KEY (`" + fkCol + "`) REFERENCES `" + refTable + "`(`" + refPk + "`)");
                    }
                }

                if (parts.isEmpty()) continue;

                StringBuilder query = new StringBuilder();
                query.append("CREATE TABLE `").append(tableName).append("` (\n");
                query.append(String.join(",\n", parts));
                query.append("\n);\n");

                writer.write(query.toString());
                writer.write(System.lineSeparator());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executeSQLFile(sqlFile);
    }

    public static void executeSQLFile(File sqlFile) {
        String url = "jdbc:mysql://localhost:3306";
        String user = "root";
        String password = "Parth@29";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            String sqlContent;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(sqlFile), StandardCharsets.UTF_8))) {
                sqlContent = br.lines().collect(Collectors.joining("\n"));
            }

            String[] queries = sqlContent.split(";");
            for (String q : queries) {
                String query = q.trim();
                if (query.isEmpty()) continue;
                stmt.execute(query);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String datatypeUpdater(DBFField field) {
        char dt = (char) field.getDataType();
        switch (dt) {
            case 'I': return "INT";
            case 'C': return String.format("VARCHAR(%d)", field.getFieldLength());
            case 'F':
            case 'N': return String.format("DECIMAL(%d,%d)", field.getFieldLength(), field.getDecimalCount());
            case 'Y': return "DECIMAL(19,4)";
            case 'D': return "DATE";
            case 'T': return "DATETIME";
            case 'L': return "BOOLEAN";
            case 'M':
            case 'V': return "TEXT";
            case 'B': return "DOUBLE";
            case 'G':
            case 'P': return "BLOB";
            default: return "VARCHAR(255)";
        }
    }

    private static String sanitizeIdentifier(String s) {
        if (s == null || s.isEmpty()) return "converted_db";
        String t = s.replaceAll("[^A-Za-z0-9_$]", "_");
        if (Character.isDigit(t.charAt(0))) t = "_" + t;
        return t;
    }
}
