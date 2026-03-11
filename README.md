# FoxMorph

**Visual FoxPro → MySQL migration tool with a polished desktop UI**

FoxMorph reads `.dbc` database catalogs and `.dbf` table files from legacy Visual FoxPro projects and produces a clean, executable MySQL SQL file — complete with schema, data, constraints, and indexes. It ships with a JavaFX desktop UI so non-technical users can run migrations without touching a command line.

---

## Table of Contents

- [What FoxMorph Migrates](#what-foxmorph-migrates)
- [What It Does Not (Yet) Migrate](#what-it-does-not-yet-migrate)
- [How It Works](#how-it-works)
- [Getting Started](#getting-started)
- [Using the UI](#using-the-ui)
- [Connection Profiles](#connection-profiles)
- [Output Format](#output-format)
- [Data Quality & Filtering](#data-quality--filtering)
- [Conversion History](#conversion-history)
- [Project Structure](#project-structure)
- [Known Limitations](#known-limitations)
- [Requirements](#requirements)

---

## What FoxMorph Migrates

### Schema

| VFP Concept | MySQL Output |
|---|---|
| `.dbc` catalog → table names | `CREATE DATABASE` + `USE` |
| `.dbf` column definitions | `CREATE TABLE` with mapped types |
| `CHARACTER` / `VARCHAR` | `VARCHAR(n)` |
| `NUMERIC` / `FLOAT` / `DOUBLE` | `DECIMAL(p,s)` / `FLOAT` / `DOUBLE` |
| `DATE` / `DATETIME` | `DATE` / `DATETIME` |
| `LOGICAL` | `BOOLEAN` |
| `MEMO` | `MEDIUMTEXT` |
| `BLOB` / `GENERAL` | `MEDIUMBLOB` |
| `INTEGER` / `AUTOINCREMENT` | `INT` / `INT AUTO_INCREMENT` |

### Constraints & Indexes

FoxMorph parses Index rows from the `.dbc` catalog and resolves them to real column names using fuzzy matching against the actual `.dbf` field list.

| Index tag prefix | MySQL output |
|---|---|
| `PK_` / `PK` | `PRIMARY KEY (column)` |
| `FK_` / `FK` | `FOREIGN KEY (col) REFERENCES table(pk)` |
| `UK_` / `UK` | `CREATE UNIQUE INDEX ...` |
| anything else | `CREATE INDEX ...` |

**FK parent-table resolution** is fully automatic — FoxMorph cross-references each FK column against the primary keys of every other table, using exact matching, substring matching, and plural-table-name heuristics (e.g. `categoryid` → table `categories`).

### Row Data

- All rows from every resolved `.dbf` file are exported as batched `INSERT` statements (500 rows per batch by default).
- **Memo fields** (`.fpt` files) are read block-by-block using the FPT block-size header and included as escaped string values.
- Auto-increment columns fall back to an internal counter when the source value is absent or corrupt, ensuring no gaps break the `PRIMARY KEY` constraint.
- Rows with corrupt headers are skipped with a warning rather than aborting the entire table.

### Stored Procedures *(experimental — output requires manual review)*

FoxPro stored procedure source is extracted from the `.dbc` catalog's FPT memo blocks and converted to MySQL `DELIMITER $$ ... END $$` syntax. The converter:

- Extracts the procedure name and parameter list
- Maps FoxPro Hungarian-notation prefixes to MySQL types (`tc` → `VARCHAR`, `tn` → `DECIMAL`, `tl` → `BOOLEAN`, `td` → `DATE`, `ti` → `INT`)
- Wraps the body in `BEGIN ... END`

> **Note:** Stored procedure conversion is best-effort. Complex FoxPro procedure bodies (local variables, `SCATTER`, `GATHER`, cursor loops, etc.) will not translate correctly and must be reviewed and rewritten by hand before execution.

---

## What It Does Not (Yet) Migrate

- Views
- Triggers
- VFP report forms (`.frx`) and label forms (`.lbx`)
- VFP menus (`.mnx`)
- Complex stored procedure bodies (see above)
- Multi-file `.dbc` databases (only one `.dbc` per source folder is supported)

---

## How It Works

```
Source Folder
  ├── mydb.dbc        ← catalog: tables, indexes, FK/PK definitions
  ├── mydb.dct        ← catalog memo blocks (FPT-format)
  ├── customers.dbf   ← table data
  ├── customers.fpt   ← memo field data for customers
  ├── orders.dbf
  └── ...

        │
        ▼  FoxMorph pipeline

1. DbcCatalogReader  — raw-byte parse of .dbc to discover table names,
                        index tags, PK/FK expressions, procedure source

2. DdlWriter         — opens each .dbf, maps VFP column types → MySQL,
                        resolves PKs/FKs/indexes to actual column names,
                        writes CREATE TABLE + index statements

3. DmlWriter         — streams each .dbf row-by-row, reads FPT memo
                        blocks, batches INSERT statements (500 rows/batch)

4. ProcedureConverter — converts FoxPro PROCEDURE blocks to MySQL syntax

        │
        ▼

Destination Folder
  └── converted.sql   ← ready to review and execute
```

After generation, the SQL file can be reviewed in the built-in syntax-highlighted editor and executed directly from the UI against a running MySQL instance.

---

## Getting Started

### Prerequisites

- Java 21 or later
- MySQL 8.x running and accessible
- Maven (to build from source)

### Build

```bash
git clone https://github.com/your-org/foxmorph.git
cd foxmorph
mvn clean package -q
```

### Run

```bash
java -jar target/foxmorph.jar
```

A login dialog will appear on startup. Enter your MySQL credentials (or load a saved profile) to continue.

---

## Using the UI

### Step 1 — Connect

When FoxMorph launches, the **MySQL Connection** dialog appears.

| Field | Description |
|---|---|
| Host | MySQL server hostname or IP (default: `localhost`) |
| Port | MySQL port (default: `3306`) |
| Username | MySQL user with `CREATE`, `INSERT`, `DROP` privileges |
| Password | MySQL password |
| Profile Name | Optional label to save this connection for reuse |

Click **Connect** to proceed.

### Step 2 — Convert

Open the **Converter** tab.

| Field | What to enter |
|---|---|
| Source DBC Folder | Folder containing your `.dbc`, `.dbf`, and `.fpt` files |
| Destination Folder | Where FoxMorph will write the `.sql` output file |
| Output File Name | Name for the generated SQL file (e.g. `mydb.sql`) |

Click **Generate SQL**. Progress appears in the live console log at the bottom of the tab. The log color-codes each line:

- **Green** — table converted successfully
- **Yellow** — table skipped or warning
- **Red** — fatal error
- **Blue** — pipeline start / milestone

### Step 3 — Review

FoxMorph automatically switches to the **SQL Preview** tab once generation completes.

- Editable SQL editor with monospace font rendering
- Editable — you can fix anything before running
- **Copy SQL** button copies the entire file to the clipboard

### Step 4 — Execute

Click **Run Against MySQL** in the SQL Preview tab to execute the file against the connected database. Status updates appear in the toolbar. All executions are recorded in the History tab.

> **Tip:** You can edit the SQL in the preview before running. FoxMorph writes your edits back to disk before executing.

---

## Connection Profiles

Profiles are stored at:

- **Windows:** `%APPDATA%\FoxMorph\profiles.json`
- **Other:** `~/.foxmorph/profiles.json`

From the connection dialog you can:

| Button | Action |
|---|---|
| **Save** | Save current fields as a named profile |
| **Load** | Populate fields from the selected profile |
| **Delete** | Remove the selected profile permanently |

Credentials are stored in plain JSON. Do not use FoxMorph profiles for credentials in shared or production environments.

---

## Output Format

A typical generated file looks like:

```sql
CREATE DATABASE IF NOT EXISTS `mydb`;
USE `mydb`;

DROP TABLE IF EXISTS `customers`;
CREATE TABLE `customers` (
  `custid`    INT AUTO_INCREMENT,
  `name`      VARCHAR(100),
  `city`      VARCHAR(50),
  `notes`     MEDIUMTEXT,
  PRIMARY KEY (`custid`)
);
CREATE INDEX `idx_customers_city` ON `customers` (`city`);

INSERT INTO `customers` (`custid`, `name`, `city`, `notes`) VALUES
(1, 'Acme Corp', 'New York', 'Key account'),
(2, 'Globex',    'Chicago',  NULL),
...
(500, ...);

INSERT INTO `customers` (...) VALUES
(501, ...), ...;
```

Tables are written in catalog order. Each table is preceded by `DROP TABLE IF EXISTS` so the script is safely re-runnable.

---

## Data Quality & Filtering

FoxMorph applies several defensive transformations during migration:

| Situation | Behaviour |
|---|---|
| Corrupt or unreadable row | Logged as a warning, row skipped, migration continues |
| Numeric column with non-numeric data | Value replaced with `NULL` |
| Auto-increment column with missing/invalid value | Replaced with an internal monotonic counter |
| Empty memo block pointer | Stored as `NULL` rather than an empty string |
| FPT block beyond end of file | Skipped with a diagnostic log line |
| String value containing single quotes | Escaped (`''`) before insertion |
| String value containing backslashes | Escaped (`\\`) before insertion |
| `.dbf` listed in catalog but file missing on disk | Table skipped with a warning; rest of migration continues |
| Multiple `.dbc` files in source folder | Migration aborted with an error — one `.dbc` per folder is required |

---

## Conversion History

Every successful and failed execution is appended to:

- **Windows:** `%APPDATA%\FoxMorph\history.json`
- **Other:** `~/.foxmorph/history.json`

The **History** tab displays:

| Column | Details |
|---|---|
| Timestamp | Date and time of execution |
| DBC File | Source `.dbc` filename |
| Database | Target database name |
| Tables | Number of tables converted |
| Rows | Total rows inserted |
| Status | Success or Failed (hover a failed row to see the error message) |

Use **Clear All** to wipe the history file. The last 100 entries are retained automatically.

---

## Project Structure

```
src/main/java/org/example/
  config/
    AppConfig.java          — connection settings + active profile
    ProfileStore.java       — save/load named MySQL profiles (JSON)
    HistoryStore.java       — persist conversion history (JSON)
  converter/
    ConversionPipeline.java — orchestrates generate → execute flow
    DbcCatalogReader.java   — raw-byte parser for .dbc catalog files
    DdlWriter.java          — CREATE TABLE / INDEX / FK generation
    DmlWriter.java          — batched INSERT generation from .dbf rows
    ProcedureConverter.java — FoxPro → MySQL stored procedure translation
  db/
    FptReader.java          — reads VFP FPT memo file blocks
    SqlExecutor.java        — executes SQL file against MySQL via JDBC
  model/
    SchemaInfo.java         — holds tables, PKs, FKs, indexes, procedures
    ForeignKeyInfo.java     — FK column + referenced table
    IndexInfo.java          — index name, column, uniqueness flag
  ui/
    FoxMorphApp.java        — JavaFX Application entry point, sidebar, panel switching with fade transitions
    ConverterView.java      — form + live log console (JavaFX VBox/TextFlow)
    SqlPreviewView.java     — SQL editor + run button (JavaFX TextArea)
    HistoryView.java        — conversion history TableView with observable row model
    LoginStage.java         — undecorated modal MySQL connection + profile management
    AppTheme.java           — CSS stylesheet + colour constants
    FxComponents.java       — reusable styled button/field/card factory
  util/
    ColumnMatcher.java      — fuzzy matching of index tags to column names
    DbfTypeMapper.java      — VFP DBF type → MySQL type string
    StringUtils.java        — SQL escaping, identifier sanitization
```

---

## Known Limitations

- **One `.dbc` per folder.** FoxMorph expects exactly one catalog file per source directory.
- **Stored procedures are experimental.** Simple parameter-passing procedures translate reasonably well. Anything involving cursors, `SCATTER`/`GATHER`, `SEEK`, or FoxPro-specific functions will need manual rewriting.
- **No incremental migration.** Every run regenerates the full SQL file and drops/recreates all tables. There is no diff or upsert mode.
- **FPT memo resolution uses a per-table heuristic.** Memo data for a table is read from the `.fpt` file with the same base name as the `.dbf`. Memo files with non-matching names will not be found.
- **CP1252 encoding assumed.** All string and memo data is decoded as Windows-1252. Databases using other code pages will need manual encoding configuration in `DbcCatalogReader` and `DmlWriter`.

---

## Requirements

| Dependency | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime |
| JavaFX | 21+ | Desktop UI framework |
| MySQL Connector/J | 8.x | JDBC driver for execution |
| JavaDBF (`com.linuxense`) | latest | DBF file reading |
| MySQL Server | 8.x | Target database |

Add to `pom.xml`:

```xml
<dependency>
  <groupId>com.linuxense</groupId>
  <artifactId>javadbf</artifactId>
  <version>1.14.0</version>
</dependency>
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>8.3.0</version>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-controls</artifactId>
  <version>21</version>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-fxml</artifactId>
  <version>21</version>
</dependency>
```

---

*FoxMorph is not affiliated with Microsoft, the Visual FoxPro project, or Oracle/MySQL.*
