package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All schema metadata extracted from the DBC catalog,
 * passed as a single object instead of scattered maps.
 */
public class SchemaInfo {

    /** Ordered list of table names as found in the catalog. */
    public final List<String> tables = new ArrayList<>();

    /** table name → primary key column tag */
    public final Map<String, String> primaryKeys = new HashMap<>();

    /** table name → foreign key list */
    public final Map<String, List<ForeignKeyInfo>> foreignKeys = new HashMap<>();

    /** table name → index list */
    public final Map<String, List<IndexInfo>> indexes = new HashMap<>();

    /** Stored procedure source blocks extracted from the catalog memo fields. */
    public final List<String> procedures = new ArrayList<>();
}
