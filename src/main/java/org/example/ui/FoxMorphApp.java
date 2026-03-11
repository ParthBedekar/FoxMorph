package org.example.ui;

import javafx.animation.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.example.config.AppConfig;
import org.example.converter.ConversionPipeline;

import java.io.File;

/**
 * JavaFX entry point — replaces FoxMorph.java (Swing).
 *
 * Key improvements over Swing:
 *  - CSS drives all styling — no paintComponent overrides
 *  - VBox/HBox replace GridBagLayout hacks
 *  - FadeTransition on panel switches (free in FX)
 *  - Drop shadows on cards via CSS effect
 *  - Crisp font rendering on all DPI settings
 */
public class FoxMorphApp extends Application {

    private final ConverterView   converterView  = new ConverterView(null); // stage set after start()
    private final SqlPreviewView  previewView    = new SqlPreviewView(this::onRunSql);
    private final HistoryView     historyView    = new HistoryView();

    private final StackPane contentArea = new StackPane();
    private Button          activeNavBtn;
    private Label           profileChip;

    private AppConfig config;

    @Override
    public void start(Stage stage) {
        // ── Root layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");
        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(contentArea);

        // Stack all views — only one visible at a time
        contentArea.getChildren().addAll(historyView, previewView, converterView);
        showView(converterView);

        converterView.setOnSqlGenerated(sqlFile -> {
            previewView.loadFile(sqlFile);
            showView(previewView);
            // Highlight the Preview nav button
            // (handled inside showView via activateNav)
        });

        // ── Scene ──────────────────────────────────────────────────────────────
        Scene scene = new Scene(root, 1150, 760);
        scene.getStylesheets().add("data:text/css," +
                java.net.URLEncoder.encode(AppTheme.CSS, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20"));

        stage.setTitle("FoxMorph");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();

        // ── Login (modal) ──────────────────────────────────────────────────────
        LoginStage login = new LoginStage(stage);
        login.showAndWait();

        if (login.isConfirmed()) {
            config = login.getConfig();
            converterView.setConfig(config);
            previewView.setConfig(config);
            if (config.getProfileName() != null && !config.getProfileName().isEmpty()) {
                profileChip.setText("Profile: " + config.getProfileName());
                profileChip.setVisible(true);
                profileChip.setManaged(true);
            }
        } else {
            javafx.application.Platform.exit();
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        // Logo
        Label logo = new Label("FoxMorph");
        logo.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 18px;");

        // Divider pip
        Label pip = new Label("|");
        pip.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 16px;");

        // Subtitle
        Label sub = new Label("Visual FoxPro  \u2192  MySQL");
        sub.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 13px;");

        HBox left = new HBox(14, logo, pip, sub);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        // Profile chip — hidden until logged in
        profileChip = new Label();
        profileChip.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-background-color: #f3f4f6; -fx-background-radius: 12; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 3 12 3 12;");
        profileChip.setVisible(false);
        profileChip.setManaged(false);

        HBox right = new HBox(profileChip);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(left, right);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(0, 24, 0, 24));
        bar.setPrefHeight(58);
        bar.setStyle("-fx-background-color: #ffffff; -fx-border-color: transparent transparent #e5e7eb transparent; -fx-border-width: 1;");
        return bar;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        Button b1 = navButton("Converter", converterView);
        Button b2 = navButton("Preview",   previewView);
        Button b3 = navButton("History",   historyView);

        VBox sidebar = new VBox(4, b1, b2, b3);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #ffffff; " +
                "-fx-border-color: transparent #e5e7eb transparent transparent; -fx-border-width: 1;");

        // Activate first button
        activateNav(b1);
        return sidebar;
    }

    private Button navButton(String label, Pane view) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            showView(view);
            activateNav(btn);
            if (view == historyView) historyView.refresh();
        });
        return btn;
    }

    private void activateNav(Button btn) {
        if (activeNavBtn != null) {
            activeNavBtn.getStyleClass().remove("nav-btn-active");
            if (!activeNavBtn.getStyleClass().contains("nav-btn"))
                activeNavBtn.getStyleClass().add("nav-btn");
        }
        btn.getStyleClass().remove("nav-btn");
        btn.getStyleClass().add("nav-btn-active");
        activeNavBtn = btn;
    }

    // ── Panel switching with fade ─────────────────────────────────────────────

    private void showView(Pane target) {
        for (var node : contentArea.getChildren()) {
            if (node == target) {
                node.setVisible(true);
                FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(150), node);
                ft.setFromValue(0.4);
                ft.setToValue(1.0);
                ft.play();
            } else {
                node.setVisible(false);
            }
        }
    }

    // ── Run SQL ───────────────────────────────────────────────────────────────

    private void onRunSql() {
        File sqlFile = previewView.getCurrentSqlFile();
        if (sqlFile == null || !sqlFile.exists()) return;

        previewView.setRunning(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override protected Void call() throws Exception {
                previewView.flushEditsToFile();
                new ConversionPipeline(config).execute(sqlFile);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            previewView.setRunning(false);
            previewView.setStatus("Executed successfully against MySQL");
            historyView.refresh();
        });

        task.setOnFailed(e -> {
            previewView.setRunning(false);
            Throwable c = task.getException();
            previewView.setStatus("Error: " + (c != null ? c.getMessage() : "unknown"));
            historyView.refresh();
        });

        new Thread(task).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}