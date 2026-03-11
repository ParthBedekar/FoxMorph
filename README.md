# FoxMorph

**Visual FoxPro → MySQL migration tool with a polished desktop UI**

---

## Download

> **[⬇ Download FoxMorph (Google Drive)](https://drive.google.com/drive/folders/1rFwTbW0KY34eKLhBZ0ForHXR6lJseFIL?usp=sharing)**

The Drive folder contains:
- **`FoxMorph.zip`** — the ready-to-run distribution package
- **`gallery/`** — screenshots of the UI, conversion output, and sample results

---

## What's in the ZIP

After extracting `FoxMorph.zip` you will find:

```
FoxMorph/
├── foxmorph.jar              ← the runnable fat JAR (everything bundled)
├── run.bat                   ← Windows launcher (double-click to start)
├── run.sh                    ← Linux/macOS launcher
├── sample/
│   ├── TestDB.dbc            ← sample VFP database catalog
│   ├── TestDB.dct            ← sample catalog memo blocks
│   ├── categories.dbf        ← sample table
│   ├── products.dbf          ← sample table
│   └── suppliers.dbf         ← sample table
└── README.txt                ← quick-start reminder
```

> **Do not rename or move `foxmorph.jar` outside the folder.** The launcher scripts reference it by relative path.

---

## Running FoxMorph

### Prerequisites

- **Java 21 or later** must be installed and on your `PATH`
  - Check: `java -version`
  - Download: https://adoptium.net
- **MySQL 8.x** running and accessible (local or remote)

### Option 1 — Double-click launcher *(recommended)*

- **Windows:** double-click `run.bat`
- **Linux / macOS:** run `./run.sh` from a terminal

### Option 2 — Command line

```bash
java -jar foxmorph.jar
```

### ⚠ Do NOT use your IDE's Run button

Running FoxMorph through IntelliJ, Eclipse, or any other IDE's built-in run configuration will likely fail with:

```
Error: JavaFX runtime components are missing
```

or

```
Error occurred during initialization of boot layer
java.lang.module.FindException: Module javafx.controls not found
```

This happens because IDEs launch the JAR with `-classpath` instead of `--module-path`, which breaks JavaFX's module system. Always use the launcher scripts or the `java -jar` command directly instead.

### Building from source

If you want to build and produce the distribution package yourself:

```bash
git clone https://github.com/your-org/foxmorph.git
cd foxmorph
mvn clean install
```

The output will be in:

```
target/
└── dist/
    ├── foxmorph.jar
    ├── run.bat
    └── run.sh
```

> Use `mvn clean install`, not `mvn clean package`. The `install` goal runs the full assembly and copies everything into `target/dist/`. Running `package` alone will produce the JAR but not the launcher scripts or the distribution folder structure.

---

## First Launch

A **MySQL Connection** dialog appears on startup. Fill in your database credentials:

| Field | Description |
|---|---|
| Host | MySQL server hostname or IP (default: `localhost`) |
| Port | MySQL port (default: `3306`) |
| Username | MySQL user with `CREATE`, `INSERT`, `DROP` privileges |
| Password | MySQL password |
| Profile Name | Optional label to save this connection for reuse |

Click **Connect** to proceed. You can save the connection as a named profile so you don't have to re-enter it each time.

---

## Using the UI

### Step 1 — Converter tab

| Field | What to enter |
|---|---|
| Source DBC Folder | Folder containing your `.dbc`, `.dbf`, and `.fpt` files |
| Destination Folder | Where FoxMorph will write the `.sql` output file |
| Output File Name | Name for the generated SQL file (e.g. `mydb.sql`) |

Click **Generate SQL**. The live console log at the bottom of the tab shows progress, color-coded by severity:

| Color | Meaning |
|---|---|
| Green | Table converted successfully |
| Yellow | Table skipped or warning |
| Red | Fatal error |
| Blue | Pipeline start / major milestone |

### Step 2 — SQL Preview tab

Once generation completes, FoxMorph automatically switches to the SQL Preview tab. Here you can:

- Read through the generated SQL with monospace rendering
- Edit anything before running (the file is saved back to disk before execution)
- Click **Copy SQL** to copy the entire file to clipboard

### Step 3 — Execute

Click **Run Against MySQL** to execute the SQL file against the connected database. Status updates appear in the toolbar above the editor.

> **Tip:** Any edits you make in the preview are written back to the `.sql` file before execution, so what you see is exactly what runs.

### Step 4 — History tab

Every run (successful or failed) is recorded in the History tab, showing timestamp, source file, database name, table count, row count, and status. Use **Clear All** to wipe the history.

---

## Connection Profiles

Profiles are stored at:

- **Windows:** `%APPDATA%\FoxMorph\profiles.json`
- **Other:** `~/.foxmorph/profiles.json`

From the connection dialog:

| Button | Action |
|---|---|
| **Save** | Save current fields as a named profile |
| **Load** | Populate fields from the selected profile |
| **Delete** | Remove the selected profile permanently |

> Credentials are stored in plain JSON. Do not use FoxMorph profiles for credentials in shared or production environments.

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

| Index tag prefix | MySQL output |
|---|---|
| `PK_` / `PK` | `PRIMARY KEY (column)` |
| `FK_` / `FK` | `FOREIGN KEY (col) REFERENCES table(pk)` |
| `UK_` / `UK` | `CREATE UNIQUE INDEX ...` |
| anything else | `CREATE INDEX ...` |

FK parent-table resolution is fully automatic — FoxMorph cross-references each FK column against the primary keys of every other table using exact matching, substring matching, and plural-table-name heuristics (e.g. `categoryid` → table `categories`).

### Row Data

- All rows are exported as batched `INSERT` statements (500 rows per batch)
- Memo fields (`.fpt` files) are read block-by-block and included as escaped string values
- Auto-increment columns fall back to an internal counter when the source value is absent or corrupt
- Rows with corrupt headers are skipped with a warning rather than aborting the migration

### Stored Procedures *(experimental)*

FoxPro stored procedure source is extracted from the `.dbc` catalog's FPT memo blocks and converted to MySQL `DELIMITER $$ ... END $$` syntax. Simple procedures with typed parameters translate reasonably well. Anything involving cursors, `SCATTER`/`GATHER`, `SEEK`, or FoxPro-specific functions will need manual rewriting before execution.

---

## What It Does Not (Yet) Migrate

- Views
- Triggers
- VFP report forms (`.frx`) and label forms (`.lbx`)
- VFP menus (`.mnx`)
- Complex stored procedure bodies
- Multi-file `.dbc` databases — one `.dbc` per source folder is required

---

## Output Format

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
```

Tables are written in catalog order. Each table is preceded by `DROP TABLE IF EXISTS` so the script is safely re-runnable.

---

## Data Quality & Filtering

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
| Multiple `.dbc` files in source folder | Migration aborted with an error |

---

## Conversion History

Every run is appended to:

- **Windows:** `%APPDATA%\FoxMorph\history.json`
- **Other:** `~/.foxmorph/history.json`

| Column | Details |
|---|---|
| Timestamp | Date and time of execution |
| DBC File | Source `.dbc` filename |
| Database | Target database name |
| Tables | Number of tables converted |
| Rows | Total rows inserted |
| Status | Success or Failed |

The last 100 entries are retained automatically.

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
    FoxMorphApp.java        — JavaFX Application entry point, sidebar, panel switching
    ConverterView.java      — form + live log console
    SqlPreviewView.java     — SQL editor + run button
    HistoryView.java        — conversion history TableView
    LoginStage.java         — modal MySQL connection + profile management
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
- **Stored procedures are experimental.** Simple procedures translate reasonably well; anything involving cursors or FoxPro-specific functions needs manual rewriting.
- **No incremental migration.** Every run drops and recreates all tables. There is no diff or upsert mode.
- **FPT memo resolution uses base-name matching.** Memo data is read from the `.fpt` file with the same base name as the `.dbf`. Non-matching names will not be found.
- **CP1252 encoding assumed.** All string and memo data is decoded as Windows-1252. Other code pages need manual configuration in `DbcCatalogReader` and `DmlWriter`.

---

## Requirements

| Dependency | Version | Purpose |
|---|---|---|
| Java | 21+ | Runtime |
| JavaFX | 21+ | Desktop UI framework |
| MySQL Connector/J | 9.x | JDBC driver for execution |
| JavaDBF (`com.linuxense`) | 0.4.0 | DBF file reading |
| MySQL Server | 8.x | Target database |

---

*FoxMorph is not affiliated with Microsoft, the Visual FoxPro project, or Oracle/MySQL.*
