package org.example.model;

/**
 * Represents a foreign key constraint parsed from the DBC catalog.
 */
public class ForeignKeyInfo {
    public final String column;
    public String referencesTable; // resolved later during relation pass

    public ForeignKeyInfo(String column, String referencesTable) {
        this.column = column;
        this.referencesTable = referencesTable;
    }
}
