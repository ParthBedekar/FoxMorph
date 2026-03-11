package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import org.example.config.AppConfig;
import org.example.config.ProfileStore;
import org.example.config.ProfileStore.Profile;

/**
 * Login / connection dialog — JavaFX modal stage.
 * Same logic as LoginDialog.java but using FX layouts.
 */
public class LoginStage extends Stage {

    private AppConfig result;
    private boolean   confirmed = false;

    private final ComboBox<String> profileBox = FxComponents.darkComboBox();
    private final TextField   nameField = FxComponents.darkTextField("My Profile");
    private final TextField   hostField = FxComponents.darkTextField("localhost");
    private final TextField   portField = FxComponents.darkTextField("3306");
    private final TextField   userField = FxComponents.darkTextField("Username");
    private final PasswordField passField = FxComponents.darkPasswordField();

    public LoginStage(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UNDECORATED);
        setResizable(false);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 10; " +
                "-fx-border-color: #e5e7eb; -fx-border-radius: 10; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");

        root.getChildren().addAll(buildHeader(), buildForm(), buildFooter());

        Scene scene = new Scene(root, 460, 520);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add("data:text/css," +
                java.net.URLEncoder.encode(AppTheme.CSS, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20"));
        setScene(scene);
        centerOnScreen();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10 10 0 0; " +
                "-fx-border-color: transparent transparent #e5e7eb transparent; -fx-border-width: 1;");
        header.setPadding(new Insets(18, 20, 18, 20));
        header.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(3);
        Label title = new Label("MySQL Connection");
        title.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 15px;");
        Label sub = new Label("Enter credentials or load a saved profile");
        sub.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
        text.getChildren().addAll(title, sub);
        header.getChildren().add(text);
        return header;
    }

    // ── Form ──────────────────────────────────────────────────────────────────

    private VBox buildForm() {
        VBox form = new VBox(4);
        form.setPadding(new Insets(20, 24, 8, 24));
        VBox.setVgrow(form, Priority.ALWAYS);

        // Profile row
        form.getChildren().add(FxComponents.fieldLabel("Saved Profile"));

        Button loadBtn   = FxComponents.secondaryButton("Load");
        Button saveBtn   = FxComponents.primaryButton("Save");
        Button deleteBtn = FxComponents.dangerButton("Delete");

        HBox profileRow = new HBox(6, profileBox, loadBtn, saveBtn, deleteBtn);
        profileRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(profileBox, Priority.ALWAYS);
        form.getChildren().add(profileRow);

        form.getChildren().add(new Region() {{ setMinHeight(6); }});
        form.getChildren().add(new Separator());
        form.getChildren().add(new Region() {{ setMinHeight(6); }});

        // Fields
        form.getChildren().addAll(
                FxComponents.fieldLabel("Profile Name"), nameField,
                FxComponents.fieldLabel("Host"),         hostField,
                FxComponents.fieldLabel("Port"),         portField,
                FxComponents.fieldLabel("Username"),     userField,
                FxComponents.fieldLabel("Password"),     passField
        );

        // Wire profile actions
        refreshProfileBox();

        loadBtn.setOnAction(e -> {
            String sel = profileBox.getValue();
            ProfileStore.loadAll().stream()
                    .filter(p -> p.name().equals(sel))
                    .findFirst().ifPresent(this::populateFields);
        });

        saveBtn.setOnAction(e -> {
            String n = nameField.getText().trim();
            if (n.isEmpty()) { showAlert("Enter a profile name first."); return; }
            ProfileStore.upsert(new Profile(n, hostField.getText().trim(),
                    portField.getText().trim(), userField.getText().trim(),
                    passField.getText()));
            refreshProfileBox();
            profileBox.setValue(n);
        });

        deleteBtn.setOnAction(e -> {
            String sel = profileBox.getValue();
            if (sel == null || sel.startsWith("—")) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete profile \"" + sel + "\"?", ButtonType.YES, ButtonType.NO);
            confirm.initOwner(this);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) { ProfileStore.delete(sel); refreshProfileBox(); }
            });
        });

        passField.setOnAction(e -> onConnect());

        return form;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private HBox buildFooter() {
        Button cancelBtn  = FxComponents.secondaryButton("Cancel");
        Button connectBtn = FxComponents.primaryButton("Connect");
        connectBtn.setDefaultButton(true);

        cancelBtn.setOnAction(e -> {
            new Alert(Alert.AlertType.WARNING, "A MySQL connection is required to continue.")
                    .showAndWait();
        });
        connectBtn.setOnAction(e -> onConnect());

        HBox footer = new HBox(8, cancelBtn, connectBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 16, 20));
        footer.setStyle("-fx-border-color: #e5e7eb transparent transparent transparent; -fx-border-width: 1;");
        return footer;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshProfileBox() {
        String cur = profileBox.getValue();
        profileBox.getItems().clear();
        profileBox.getItems().add("— select profile —");
        ProfileStore.loadAll().forEach(p -> profileBox.getItems().add(p.name()));
        profileBox.setValue(cur != null ? cur : "— select profile —");
    }

    private void populateFields(Profile p) {
        nameField.setText(p.name());
        hostField.setText(p.host()     != null ? p.host()     : "localhost");
        portField.setText(p.port()     != null ? p.port()     : "3306");
        userField.setText(p.username() != null ? p.username() : "");
        passField.setText(p.password() != null ? p.password() : "");
    }

    private void onConnect() {
        String user = userField.getText().trim();
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        if (user.isEmpty()) { showAlert("Username cannot be empty."); return; }
        result    = new AppConfig(user, passField.getText(),
                host.isEmpty() ? "localhost" : host,
                port.isEmpty() ? "3306"      : port,
                nameField.getText().trim());
        confirmed = true;
        close();
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg);
        a.initOwner(this);
        a.showAndWait();
    }

    public boolean   isConfirmed() { return confirmed; }
    public AppConfig getConfig()   { return result;    }
}