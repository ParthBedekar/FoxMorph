package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.example.config.AppConfig;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.*;

/**
 * SQL Preview tab — RichTextFX CodeArea with full syntax highlighting,
 * built-in line numbers, proper VirtualizedScrollPane (both scrollbars),
 * and Run / Copy toolbar.
 *
 * Add to pom.xml:
 *   <dependency>
 *     <groupId>org.fxmisc.richtext</groupId>
 *     <artifactId>richtextfx</artifactId>
 *     <version>0.11.3</version>
 *   </dependency>
 */
public class SqlPreviewView extends VBox {

    // ── Token regex ───────────────────────────────────────────────────────────

    private static final String[] KEYWORDS = {
            "SELECT","INSERT","UPDATE","DELETE","CREATE","DROP","ALTER","TABLE",
            "DATABASE","INDEX","UNIQUE","PRIMARY","KEY","FOREIGN","REFERENCES",
            "INTO","VALUES","FROM","WHERE","IF","NOT","EXISTS","USE","ON",
            "DEFAULT","NULL","AUTO_INCREMENT","CONSTRAINT","ADD","SET",
            "ENGINE","CHARSET","COLLATE","COMMENT","UNSIGNED","SIGNED",
            "INT","BIGINT","SMALLINT","TINYINT","MEDIUMINT",
            "VARCHAR","CHAR","TEXT","MEDIUMTEXT","LONGTEXT",
            "DATE","DATETIME","TIMESTAMP","TIME","YEAR",
            "DECIMAL","FLOAT","DOUBLE","NUMERIC","REAL",
            "BOOLEAN","BOOL","BLOB","MEDIUMBLOB","LONGBLOB","BINARY","VARBINARY",
            "DELIMITER","BEGIN","END","PROCEDURE","FUNCTION","RETURNS","RETURN",
            "DECLARE","FOR","LOOP","REPEAT","UNTIL","WHILE","DO",
            "BETWEEN","LIKE","IN","IS","AND","OR","DISTINCT","ALL",
            "UNION","JOIN","INNER","LEFT","RIGHT","OUTER","CROSS","NATURAL",
            "GROUP","BY","ORDER","HAVING","LIMIT","OFFSET","AS","WITH"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>--[^\n]*)"
                    + "|(?<STRING>'(?:[^'\\\\]|\\\\.)*')"
                    + "|(?<BACKTICK>`[^`]*`)"
                    + "|(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)"
                    + "|(?<KEYWORD>\\b(?:" + String.join("|", KEYWORDS) + ")\\b)"
                    + "|(?<PAREN>[()])"
                    + "|(?<SEMICOLON>;)",
            Pattern.CASE_INSENSITIVE
    );

    // ── Highlight CSS — Catppuccin Mocha palette ──────────────────────────────

    private static final String HIGHLIGHT_CSS =
            ".sql-editor .text                     { -fx-fill: #cdd6f4; }\n" +
                    ".sql-editor .keyword                  { -fx-fill: #89b4fa; -fx-font-weight: bold; }\n" +
                    ".sql-editor .string                   { -fx-fill: #a6e3a1; }\n" +
                    ".sql-editor .comment                  { -fx-fill: #6c7086; -fx-font-style: italic; }\n" +
                    ".sql-editor .number                   { -fx-fill: #fab387; }\n" +
                    ".sql-editor .backtick                 { -fx-fill: #f9e2af; }\n" +
                    ".sql-editor .paren                    { -fx-fill: #cba6f7; }\n" +
                    ".sql-editor .semicolon                { -fx-fill: #f38ba8; }\n" +
                    ".sql-editor .lineno                   { -fx-background-color: #181825; " +
                    "-fx-text-fill: #45475a; " +
                    "-fx-font-family: Consolas; " +
                    "-fx-font-size: 12px; " +
                    "-fx-padding: 0 10 0 6; }\n" +
                    ".sql-editor .paragraph-box:has-caret  { -fx-background-color: #2a2a3e; }\n";

    // ── State ─────────────────────────────────────────────────────────────────

    private final CodeArea editor    = new CodeArea();
    private final Label    statusLbl = new Label("No SQL generated yet");
    private final Button   runBtn    = FxComponents.primaryButton("Run Against MySQL");
    private final Button   copyBtn   = FxComponents.secondaryButton("Copy SQL");
    private final Runnable onRun;

    private File          currentSqlFile;
    private AppConfig     config;

    // Daemon thread — async highlighting never blocks the FX thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sql-highlight");
        t.setDaemon(true);
        return t;
    });

    public SqlPreviewView(Runnable onRun) {
        this.onRun = onRun;
        setSpacing(0);

        runBtn.setDisable(true);
        copyBtn.setDisable(true);
        runBtn.setOnAction(e  -> { if (onRun != null) onRun.run(); });
        copyBtn.setOnAction(e -> copyToClipboard());

        VBox editorWrapper = buildEditor();
        getChildren().addAll(buildToolbar(), editorWrapper);
        VBox.setVgrow(editorWrapper, Priority.ALWAYS);
    }

    public void setConfig(AppConfig c) { this.config = c; }

    // ── Public API ────────────────────────────────────────────────────────────

    public void loadFile(File sqlFile) {
        this.currentSqlFile = sqlFile;
        try {
            String sql = Files.readString(sqlFile.toPath());
            editor.setEditable(true);
            editor.replaceText(sql);
            editor.moveTo(0);
            editor.requestFollowCaret();
            runBtn.setDisable(false);
            copyBtn.setDisable(false);
            long lines = sql.lines().count();
            long stmts = sql.chars().filter(c -> c == ';').count();
            statusLbl.setText(String.format("%,d lines  \u2022  %,d statements  \u2022  %s",
                    lines, stmts, sqlFile.getName()));
            statusLbl.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        } catch (Exception e) {
            editor.replaceText("Error reading file: " + e.getMessage());
        }
    }

    public File getCurrentSqlFile() { return currentSqlFile; }

    public void flushEditsToFile() throws Exception {
        if (currentSqlFile == null) return;
        Files.writeString(currentSqlFile.toPath(), editor.getText());
    }

    public void setRunning(boolean running) {
        runBtn.setDisable(running);
        runBtn.setText(running ? "Running..." : "Run Against MySQL");
    }

    public void setStatus(String msg) {
        Platform.runLater(() -> statusLbl.setText(msg));
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        statusLbl.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        HBox left = new HBox(statusLbl);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setPadding(new Insets(0, 0, 0, 12));
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox right = new HBox(8, copyBtn, runBtn);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPadding(new Insets(0, 12, 0, 0));

        HBox toolbar = new HBox(left, right);
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setPadding(new Insets(8, 0, 8, 0));
        toolbar.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-border-color: transparent transparent #e5e7eb transparent;" +
                        "-fx-border-width: 1;");
        return toolbar;
    }

    // ── Editor ────────────────────────────────────────────────────────────────

    private VBox buildEditor() {
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        editor.getStyleClass().add("sql-editor");
        editor.setWrapText(false); // allow horizontal scroll for long lines

        editor.setStyle(
                "-fx-font-family: Consolas;" +
                        "-fx-font-size: 13px;" +
                        "-fx-background-color: #1e1e2e;");

        // Inject highlight CSS once the editor attaches to a Scene
        editor.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                String encoded = java.net.URLEncoder
                        .encode(HIGHLIGHT_CSS, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20");
                String uri = "data:text/css," + encoded;
                if (!newScene.getStylesheets().contains(uri)) {
                    newScene.getStylesheets().add(uri);
                }
            }
        });

        // Async debounced re-highlight on every text change
        editor.multiPlainChanges()
                .successionEnds(Duration.ofMillis(80))
                .retainLatestUntilLater(executor)
                .supplyTask(this::highlightAsync)
                .awaitLatest(editor.multiPlainChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) return Optional.of(t.get());
                    t.getFailure().printStackTrace();
                    return Optional.empty();
                })
                .subscribe(spans -> editor.setStyleSpans(0, spans));

        editor.replaceText(
                "\n\n   Generate SQL from the Converter tab to preview it here.\n\n" +
                        "   You can review and edit the output before executing it\n" +
                        "   against your MySQL database.");
        editor.setEditable(false);

        // VirtualizedScrollPane is required for CodeArea — provides correct
        // virtualized vertical AND horizontal scrollbars. A plain JavaFX
        // ScrollPane does not work properly with CodeArea.
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(editor);
        scrollPane.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox wrapper = new VBox(scrollPane);
        wrapper.setStyle("-fx-background-color: #1e1e2e;");
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    // ── Highlighting ──────────────────────────────────────────────────────────

    private javafx.concurrent.Task<StyleSpans<Collection<String>>> highlightAsync() {
        String text = editor.getText();
        javafx.concurrent.Task<StyleSpans<Collection<String>>> task =
                new javafx.concurrent.Task<StyleSpans<Collection<String>>>() {
                    @Override
                    protected StyleSpans<Collection<String>> call() {
                        return computeSpans(text);
                    }
                };
        executor.execute(task);
        return task;
    }

    private static StyleSpans<Collection<String>> computeSpans(String text) {
        Matcher matcher = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int lastEnd = 0;

        while (matcher.find()) {
            String cls =
                    matcher.group("KEYWORD")   != null ? "keyword"   :
                            matcher.group("STRING")    != null ? "string"    :
                                    matcher.group("COMMENT")   != null ? "comment"   :
                                            matcher.group("NUMBER")    != null ? "number"    :
                                                    matcher.group("BACKTICK")  != null ? "backtick"  :
                                                            matcher.group("PAREN")     != null ? "paren"     :
                                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                                            null;

            builder.add(Collections.emptyList(), matcher.start() - lastEnd);
            builder.add(cls != null
                            ? Collections.singleton(cls)
                            : Collections.emptyList(),
                    matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        builder.add(Collections.emptyList(), text.length() - lastEnd);
        return builder.create();
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    private void copyToClipboard() {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(editor.getText());
        cb.setContent(content);
        String prev = statusLbl.getText();
        String prevStyle = statusLbl.getStyle();
        statusLbl.setText("Copied to clipboard!");
        statusLbl.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px;");
        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> {
            statusLbl.setText(prev);
            statusLbl.setStyle(prevStyle);
        });
        pause.play();
    }
}