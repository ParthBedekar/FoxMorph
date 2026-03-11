package org.example.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.example.config.AppConfig;
import org.example.converter.ConversionPipeline;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Converter tab — equivalent to ConverterPanel.java
 */
public class ConverterView extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextField  sourceField   = FxComponents.darkTextField("Path to folder containing .dbc file");
    private final TextField  destField     = FxComponents.darkTextField("Output folder for generated SQL");
    private final TextField  filenameField = FxComponents.darkTextField("converted.sql");
    private final TextFlow   logFlow       = new TextFlow();
    private final Button     convertBtn    = FxComponents.primaryButton("Generate SQL");
    private final Button     clearBtn      = FxComponents.secondaryButton("Clear");

    private final Stage       owner;
    private AppConfig         config;
    private Consumer<File>    onSqlGenerated;

    public ConverterView(Stage owner) {
        this.owner = owner;
        setBackground(new Background(new BackgroundFill(Color.web("#f5f5f5"), null, null)));
        setPadding(new Insets(24));
        setSpacing(16);
        getChildren().addAll(buildFormCard(), buildLogCard());
        VBox.setVgrow(buildLogCard(), Priority.ALWAYS);
    }

    public void setConfig(AppConfig c)                 { this.config = c; }
    public void setOnSqlGenerated(Consumer<File> cb)   { this.onSqlGenerated = cb; }

    // ── Form card ─────────────────────────────────────────────────────────────

    private VBox buildFormCard() {
        VBox card = FxComponents.card(20, 20, 20, 20);
        card.setSpacing(10);

        Label heading = FxComponents.headingLabel("Conversion Settings");
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #d4d4d4;");

        card.getChildren().addAll(
                heading,
                FxComponents.separator(),
                fieldRow("Source DBC Folder", sourceField, browseBtn(sourceField)),
                fieldRow("Output Folder",     destField,   browseBtn(destField)),
                fieldRow("SQL Filename",      filenameField, null)
        );

        convertBtn.setOnAction(e -> onGenerate());
        clearBtn.setOnAction(e -> logFlow.getChildren().clear());

        HBox btnRow = new HBox(8, convertBtn, clearBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(btnRow);

        return card;
    }

    private HBox fieldRow(String labelText, TextField field, Button btn) {
        Label label = FxComponents.fieldLabel(labelText);
        label.setMinWidth(160);
        HBox row = new HBox(10, label, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        if (btn != null) row.getChildren().add(btn);
        return row;
    }

    private Button browseBtn(TextField target) {
        Button btn = FxComponents.secondaryButton("Browse");
        btn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Folder");
            File dir = dc.showDialog(owner);
            if (dir != null) target.setText(dir.getAbsolutePath());
        });
        return btn;
    }

    // ── Log card ──────────────────────────────────────────────────────────────

    private VBox logCardRef; // kept so VGrow works

    private VBox buildLogCard() {
        VBox card = FxComponents.card(16, 16, 16, 16);
        card.setSpacing(10);
        VBox.setVgrow(card, Priority.ALWAYS);

        Label heading = FxComponents.headingLabel("Conversion Log");

        ScrollPane scroll = new ScrollPane(logFlow);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1a1a1a; -fx-background-color: #1a1a1a; -fx-border-color: #e5e7eb; -fx-border-radius: 4;");
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        logFlow.setPadding(new Insets(10));
        logFlow.setStyle("-fx-background-color: #1a1a1a;");
        logFlow.setLineSpacing(2);

        card.getChildren().addAll(heading, FxComponents.separator(), scroll);
        logCardRef = card;
        return card;
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> {
            String line = "[" + LocalTime.now().format(TIME_FMT) + "]  " + msg + "\n";
            Text t = new Text(line);
            t.setFont(Font.font("Consolas", FontWeight.NORMAL, 12));
            t.setFill(colorFor(msg));
            logFlow.getChildren().add(t);
        });
    }

    private javafx.scene.paint.Color colorFor(String m) {
        // Success — green
        if (m.contains("Done") || m.contains("[OK]") || m.contains("successfully") || m.contains("ready"))
            return Color.web("#4ec994");
        // Errors — red
        if (m.contains("[ERR]") || m.contains("Error") || m.contains("error") || m.contains("failed"))
            return Color.web("#f47c7c");
        // Warnings — amber
        if (m.contains("Warning") || m.contains("Skipping") || m.contains("warn"))
            return Color.web("#e5a84a");
        // Info / section headers — blue
        if (m.contains("[READ]") || m.contains("[EXEC]") || m.contains("[PROC]") || m.contains("Starting"))
            return Color.web("#79b8ff");
        // Timestamps / separators — very dim
        if (m.startsWith("─") || m.startsWith("-"))
            return Color.web("#444444");
        // Table processing lines — slightly brighter white
        if (m.contains("Processing") || m.contains("Resolved"))
            return Color.web("#c8c8c8");
        // Default
        return Color.web("#a0a0a0");
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    private void onGenerate() {
        String src = sourceField.getText().trim();
        String dst = destField.getText().trim();
        String fn  = filenameField.getText().trim();

        if (src.isEmpty() || dst.isEmpty() || fn.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please fill in all fields.").showAndWait();
            return;
        }
        File[] dbcs = new File(src).listFiles((d, n) -> n.toLowerCase().endsWith(".dbc"));
        if (dbcs == null || dbcs.length == 0) {
            new Alert(Alert.AlertType.ERROR, "No .dbc file found in source folder.").showAndWait();
            return;
        }
        if (dbcs.length > 1) {
            new Alert(Alert.AlertType.ERROR, "Multiple .dbc files found — ensure only one is present.").showAndWait();
            return;
        }

        String dbcPath = dbcs[0].getAbsolutePath();
        convertBtn.setDisable(true);
        appendLog("Starting SQL generation...");

        Task<File> task = new Task<>() {
            @Override protected File call() throws Exception {
                return new ConversionPipeline(config, ConverterView.this::appendLog)
                        .generate(dbcPath, dst, fn);
            }
        };

        task.setOnSucceeded(e -> {
            convertBtn.setDisable(false);
            appendLog("SQL ready - opening preview...");
            if (onSqlGenerated != null) onSqlGenerated.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            convertBtn.setDisable(false);
            Throwable c = task.getException();
            appendLog("[ERR] " + (c != null ? c.getMessage() : "Unknown error"));
        });

        new Thread(task).start();
    }
}