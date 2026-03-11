package org.example.model;

/**
 * Represents an index parsed from the DBC catalog.
 */
public class IndexInfo {
    public final String name;
    public final String column;
    public final boolean isUnique;

    public IndexInfo(String name, String column, boolean isUnique) {
        this.name = name;
        this.column = column;
        this.isUnique = isUnique;
    }
}
