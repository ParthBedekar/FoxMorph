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
    private String destinationPath;
    private String filename;
    public static class ForeignKeyInfo {
        String column;
        String referencesTable;

        public ForeignKeyInfo(String column, String referencesTable) {
            this.column = column;
            this.referencesTable = referencesTable;
        }
    }
    Converter(String dest,String name){
        this.destinationPath=dest;
        this.filename=name;
    }
    public void read(String path, File outFile) {
        String currentTable = null;
        File file = new File(path);
        HashMap<String, String> primary_keys = new HashMap<>();
        HashMap<String, ArrayList<ForeignKeyInfo>> foreign_keys = new HashMap<>();
        HashMap<Integer, String> Tables = new HashMap<>();
        int tableCounter = 1;
        if (!file.exists()) return;

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            PrintWriter writer = new PrintWriter(outFile, StandardCharsets.UTF_8);
            DBFReader reader = new DBFReader(fileInputStream);
            reader.setCharactersetName("Cp1252");

            int numberOfFields = reader.getFieldCount();
            writer.println("======DBC File METADATA======\n");
            for (int i = 0; i < numberOfFields; i++) {
                DBFField field = reader.getField(i);
                System.out.print(field.getName() + " (" + field.getDataType() + ")\t");
                writer.print(field.getName() + " (" + field.getDataType() + ")\t");
            }

            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                for (int i = 0; i < row.length; i++) {
                    Object val = row[i];
                    if (val == null) continue;

                    String valueStr = val.toString()
                            .replaceAll("\\p{Cntrl}", " ")
                            .replaceAll("[^\\x20-\\x7E]", "")
                            .replaceAll("\\s+", " ")
                            .trim();

                    if (valueStr.toLowerCase().startsWith("table ")) {
                        currentTable = valueStr.replaceAll("(?i)table\\s+(\\w+)", "$1");
                        Tables.put(tableCounter++, currentTable);
                    }

                    if (currentTable != null) {
                        if (valueStr.matches(".*\\bpk_\\w+.*")) {
                            primary_keys.put(currentTable, valueStr.replaceAll(".*\\bpk_(\\w+).*", "$1"));
                        } else if (valueStr.toLowerCase().startsWith("dex fk")) {
                            String fkName = "";
                            if (i + 1 < row.length && row[i + 1] != null) {
                                fkName = row[i + 1].toString().replaceFirst("_", "").trim();
                            }
                            if (!fkName.isEmpty()) {
                                foreign_keys.computeIfAbsent(currentTable, _ -> new ArrayList<>())
                                        .add(new ForeignKeyInfo(fkName, null));
                            }
                        } else if (valueStr.toLowerCase().startsWith("relation ")) {
                            String relationNumStr = valueStr.toLowerCase().replace("relation", "").trim();
                            int relationNum = Integer.parseInt(relationNumStr);
                            if (Tables.containsKey(relationNum)) {
                                String referencedTable = Tables.get(relationNum);
                                ArrayList<ForeignKeyInfo> fkList = foreign_keys.get(currentTable);
                                if (fkList != null && !fkList.isEmpty()) {
                                    fkList.getLast().referencesTable = referencedTable;
                                    System.out.println("Foreign key in " + currentTable + " refers to table: " + referencedTable);
                                }
                            }
                        }
                    }

                    if (!valueStr.isEmpty() && !"null".equalsIgnoreCase(valueStr)) {
                        System.out.print(valueStr + "\t");
                        writer.print(valueStr + "\t");
                    }
                }
                System.out.println();
                writer.println();
            }

            System.out.println("\n=== Primary Keys ===");
            writer.println("\n=== Primary Keys ===");
            for (String table : primary_keys.keySet()) {
                String pk = primary_keys.get(table);
                System.out.println(table + " : " + pk);
                writer.println(table + " : " + pk);
            }

            System.out.println("\n=== Foreign Keys ===");
            writer.println("\n=== Foreign Keys ===");
            for (String table : foreign_keys.keySet()) {
                ArrayList<ForeignKeyInfo> fks = foreign_keys.get(table);
                System.out.println(table + " : " + fks.stream().map(f -> f.column + "->" + f.referencesTable).toList());
                writer.println(table + " : " + fks.stream().map(f -> f.column + "->" + f.referencesTable).toList());
            }

            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        dbf(new ArrayList<>(Tables.values()), primary_keys, foreign_keys);
    }

    public  void dbf(ArrayList<String> Tables, HashMap<String, String> pks, HashMap<String, ArrayList<ForeignKeyInfo>> fks) {

        File sqlfile = new File(destinationPath, filename);
        try (FileWriter writer = new FileWriter(sqlfile, false)) {
            String db = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
            writer.write("CREATE DATABASE IF NOT EXISTS " + db + ";\n");
            writer.write("USE " + db + ";\n\n");

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (String tableName : Tables) {
            String path = "C:\\Users\\USER\\Documents\\Visual FoxPro Projects\\" + tableName + ".dbf";

            try (FileWriter writer = new FileWriter(sqlfile, true)) {
                FileInputStream dbfFile = new FileInputStream(path);
                DBFReader reader = new DBFReader(dbfFile);
                int columns = reader.getFieldCount();

                System.out.println("\nColumn Metadata for table: " + tableName);

                StringBuilder query = new StringBuilder("CREATE TABLE " + tableName + " (\n");

                // Columns
                for (int i = 0; i < columns; i++) {
                    DBFField field = reader.getField(i);
                    String columnDef = "    " + field.getName() + " " + datatypeUpdater(field);

                    query.append(columnDef);

                    if (i < columns - 1) {
                        query.append(",\n");
                    }
                }

                // Primary key
                if (pks.containsKey(tableName)) {
                    query.append(",\n    PRIMARY KEY (").append(pks.get(tableName)).append(")");
                }

                // Foreign keys
                if (fks.containsKey(tableName)) {
                    for (ForeignKeyInfo fk : fks.get(tableName)) {
                        query.append(",\n    FOREIGN KEY (")
                                .append(fk.column)
                                .append(") REFERENCES ")
                                .append(fk.referencesTable)
                                .append("(")
                                .append(pks.get(fk.referencesTable))
                                .append(")");
                    }
                }

                query.append("\n);");


                writer.write(query.toString());
                writer.write(System.lineSeparator());


                System.out.println("\nGenerated SQL for table " + tableName + ":\n" + query);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        executeSQLFile(sqlfile);
    }
    public static void executeSQLFile(File sqlFile) {
        String url = "jdbc:mysql://localhost:3306";
        String user = "root";
        String password = "Parth@29";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            System.out.println("Connected to MySQL!");

            String sqlContent;
            try (BufferedReader br = new BufferedReader(new FileReader(sqlFile))) {
                sqlContent = br.lines().collect(Collectors.joining("\n"));
            }

            String[] queries = sqlContent.split(";");
            for (String query : queries) {
                query = query.trim();
                if (!query.isEmpty()) {
                    stmt.execute(query);
                    System.out.println("Executed: " + query);
                }
            }

            System.out.println("All queries executed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static String datatypeUpdater(DBFField field) {
        char dt = (char) field.getDataType();
        switch (dt) {
            case 'I': return "INT";
            case 'C': return String.format("VARCHAR(%d)", field.getFieldLength());
            case 'F', 'N': return String.format("DECIMAL(%d,%d)", field.getFieldLength(), field.getDecimalCount());
            case 'Y': return "DECIMAL(19,4)";
            case 'D': return "DATE";
            case 'T': return "DATETIME";
            case 'L': return "BOOLEAN";
            case 'M', 'V': return "TEXT";
            case 'B': return "DOUBLE";
            case 'G': case 'P': return "BLOB";
            default: return "VARCHAR(255)";
        }
    }


}
