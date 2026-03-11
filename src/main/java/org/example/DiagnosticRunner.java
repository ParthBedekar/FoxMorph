package org.example;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import org.example.converter.DbcCatalogReader;
import org.example.db.FptReader;
import org.example.model.SchemaInfo;

import java.io.*;
import java.util.*;

/**
 * Standalone diagnostic — run this against your test DBC before doing a full
 * conversion. It reports exactly what was found for procedures and memo fields
 * so you can confirm they're being read correctly.
 *
 * Usage: pass the full path to your .dbc file as args[0]
 *   e.g.  C:\data\mydb\mydb.dbc
 */
public class DiagnosticRunner {

    public static void main(String[] args) throws Exception {
        String dbcPath = args.length > 0
                ? args[0]
                : promptForPath();

        if (dbcPath == null || dbcPath.isBlank()) {
            System.out.println("No path provided. Exiting.");
            return;
        }

        File dbcFile = new File(dbcPath);
        if (!dbcFile.exists()) {
            System.out.println("ERROR: File not found: " + dbcPath);
            return;
        }

        System.out.println("=".repeat(60));
        System.out.println("  FoxMorph Diagnostic");
        System.out.println("  DBC: " + dbcFile.getAbsolutePath());
        System.out.println("=".repeat(60));

        // ── 1. Schema parse ──────────────────────────────────────────
        System.out.println("\n[1] Parsing DBC catalog...");
        SchemaInfo schema = new DbcCatalogReader().read(dbcPath);

        System.out.println("    Tables found    : " + schema.tables.size());
        schema.tables.forEach(t -> System.out.println("      • " + t));

        System.out.println("    Primary keys    : " + schema.primaryKeys.size());
        schema.primaryKeys.forEach((t, k) -> System.out.println("      • " + t + " → " + k));

        System.out.println("    Foreign keys    : " + schema.foreignKeys.size());
        schema.foreignKeys.forEach((t, fks) -> fks.forEach(fk ->
                System.out.println("      • " + t + "." + fk.column + " → " + fk.referencesTable)));

        // ── 2. Procedures ────────────────────────────────────────────
        System.out.println("\n[2] Stored Procedures");
        System.out.println("    Found: " + schema.procedures.size());
        if (schema.procedures.isEmpty()) {
            System.out.println("    ⚠️  NONE — either no procedures exist or memo extraction failed.");
            System.out.println("       Check FPT section below for clues.");
        } else {
            for (int i = 0; i < schema.procedures.size(); i++) {
                String proc = schema.procedures.get(i);
                System.out.println("\n    --- Procedure " + (i + 1) + " ---");
                System.out.println("    Length : " + proc.length() + " chars");
                System.out.println("    Preview: " + proc.substring(0, Math.min(300, proc.length())));
            }
        }

        // ── 3. FPT / memo probe ──────────────────────────────────────
        System.out.println("\n[3] FPT Memo Probe (per table)");
        File dbcDir = dbcFile.getParentFile();

        for (String table : schema.tables) {
            File dbf = new File(dbcDir, table + ".dbf");
            File fpt = new File(dbcDir, table + ".fpt");

            System.out.println("\n  Table: " + table);
            System.out.println("    .dbf exists: " + dbf.exists());
            System.out.println("    .fpt exists: " + fpt.exists());

            if (!dbf.exists()) continue;

            // Find memo fields and sample the first non-null memo value
            try (FileInputStream fis = new FileInputStream(dbf)) {
                DBFReader reader = new DBFReader(fis);
                reader.setCharactersetName("Cp1252");
                int fieldCount = reader.getFieldCount();

                List<Integer> memoIndexes = new ArrayList<>();
                for (int i = 0; i < fieldCount; i++) {
                    char t = (char) reader.getField(i).getDataType();
                    if (t == 'M' || t == 'G' || t == 'P') {
                        memoIndexes.add(i);
                        System.out.println("    Memo field [" + i + "]: " + reader.getField(i).getName());
                    }
                }

                if (memoIndexes.isEmpty()) {
                    System.out.println("    No memo fields in this table.");
                    continue;
                }

                if (!fpt.exists()) {
                    System.out.println("    ⚠️  Memo fields exist but NO .fpt file found!");
                    continue;
                }

                // Read first 5 rows and try to extract memo content
                System.out.println("    Sampling up to 5 rows...");
                Object[] row;
                int rowNum = 0, sampled = 0;
                while ((row = reader.nextRecord()) != null && sampled < 5) {
                    rowNum++;
                    for (int mi : memoIndexes) {
                        if (mi >= row.length || row[mi] == null) continue;
                        Object raw = row[mi];
                        int pointer = 0;
                        if (raw instanceof Number n)  pointer = n.intValue();
                        else if (raw instanceof String s) {
                            try { pointer = Integer.parseInt(s.trim()); }
                            catch (NumberFormatException ignored) {}
                        }

                        System.out.printf("      Row %d, field[%d]: raw=%s  pointer=%d%n",
                                rowNum, mi, raw, pointer);

                        if (pointer > 0) {
                            String content = FptReader.read(dbf, pointer);
                            if (content != null) {
                                System.out.println("      ✅ Memo content (" + content.length() + " chars): "
                                        + content.substring(0, Math.min(120, content.length())));
                                sampled++;
                            } else {
                                System.out.println("      ❌ FptReader returned null for pointer " + pointer);
                            }
                        } else {
                            System.out.println("      ⚠️  pointer=0, no memo content to read");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("    ERROR reading table: " + e.getMessage());
            }
        }

        // ── 4. DBC FPT probe (for procedures) ───────────────────────
        System.out.println("\n[4] DBC FPT Probe (stored procedures source)");
        File dbcFpt = new File(dbcDir, stripExt(dbcFile.getName()) + ".fpt");
        System.out.println("    DBC .fpt path: " + dbcFpt.getAbsolutePath());
        System.out.println("    .fpt exists  : " + dbcFpt.exists());

        if (dbcFpt.exists()) {
            System.out.println("    .fpt size    : " + dbcFpt.length() + " bytes");

            // Read block size from FPT header bytes 6-7 (big-endian short)
            try (RandomAccessFile raf = new RandomAccessFile(dbcFpt, "r")) {
                raf.seek(6);
                int hi = raf.read(), lo = raf.read();
                int blockSize = (hi << 8) | lo;
                System.out.println("    FPT block size (from header): " + blockSize
                        + (blockSize != 512 ? "  ⚠️  NOT 512 — hardcoded block size will misread!" : "  ✓"));
            } catch (Exception e) {
                System.out.println("    Could not read FPT header: " + e.getMessage());
            }
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Diagnostic complete.");
        System.out.println("=".repeat(60));
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }

    private static String promptForPath() {
        System.out.print("Enter path to .dbc file: ");
        return new Scanner(System.in).nextLine().trim();
    }
}