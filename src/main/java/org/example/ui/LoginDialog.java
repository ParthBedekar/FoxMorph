package org.example.ui;

import org.example.config.AppConfig;
import org.example.config.ProfileStore;
import org.example.config.ProfileStore.Profile;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * MySQL login dialog with connection profile support.
 * Profiles are saved to %APPDATA%/FoxMorph/profiles.json
 */
public class LoginDialog extends JDialog {

    private AppConfig result;
    private boolean   confirmed = false;

    private final JComboBox<String> profileBox   = new JComboBox<>();
    private final JTextField        nameField    = UiFactory.accentField("My Profile");
    private final JTextField        hostField    = UiFactory.accentField("localhost");
    private final JTextField        portField    = UiFactory.accentField("3306");
    private final JTextField        userField    = UiFactory.accentField("");
    private final JPasswordField    passField    = UiFactory.accentPasswordField();

    public LoginDialog(JFrame owner) {
        super(owner, "MySQL Connection", true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(520, 440));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        panel.setBackground(Theme.BG_PANEL);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets  = new Insets(7, 8, 7, 8);
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.weightx = 0;

        Dimension labelSize = new Dimension(140, 34);
        Dimension fieldSize = new Dimension(260, 34);
        Dimension btnSmall  = new Dimension(100, 34);

        // ── Profile row ───────────────────────────────────────────────────────
        profileBox.setFont(Theme.FONT_FIELD);
        profileBox.setPreferredSize(new Dimension(180, 34));
        profileBox.setBackground(Color.WHITE);

        JButton loadBtn   = UiFactory.accentButton("Load");
        JButton saveBtn   = UiFactory.accentButton("Save");
        JButton deleteBtn = UiFactory.accentButton("Delete");
        for (JButton b : new JButton[]{loadBtn, saveBtn, deleteBtn}) {
            b.setPreferredSize(new Dimension(80, 34));
        }

        JLabel profileLabel = UiFactory.bodyLabel("Profile:");
        profileLabel.setPreferredSize(labelSize);

        JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        profileRow.setBackground(Theme.BG_PANEL);
        profileRow.add(profileBox);
        profileRow.add(loadBtn);
        profileRow.add(saveBtn);
        profileRow.add(deleteBtn);

        addRow(panel, gc, 0, profileLabel, profileRow, labelSize);

        // ── Separator ─────────────────────────────────────────────────────────
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(200, 200, 220));
        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 2; gc.weightx = 1;
        gc.insets = new Insets(2, 8, 2, 8);
        panel.add(sep, gc);
        gc.gridwidth = 1; gc.insets = new Insets(7, 8, 7, 8);

        // ── Connection fields ─────────────────────────────────────────────────
        JLabel nameLabel = UiFactory.bodyLabel("Profile Name:");
        JLabel hostLabel = UiFactory.bodyLabel("Host:");
        JLabel portLabel = UiFactory.bodyLabel("Port:");
        JLabel userLabel = UiFactory.bodyLabel("Username:");
        JLabel passLabel = UiFactory.bodyLabel("Password:");

        nameField.setPreferredSize(fieldSize);
        hostField.setPreferredSize(fieldSize);
        portField.setPreferredSize(fieldSize);
        userField.setPreferredSize(fieldSize);
        passField.setPreferredSize(fieldSize);

        for (JLabel l : new JLabel[]{nameLabel, hostLabel, portLabel, userLabel, passLabel}) {
            l.setPreferredSize(labelSize);
        }

        addRow(panel, gc, 2, nameLabel, nameField, labelSize);
        addRow(panel, gc, 3, hostLabel, hostField, labelSize);
        addRow(panel, gc, 4, portLabel, portField, labelSize);
        addRow(panel, gc, 5, userLabel, userField, labelSize);
        addRow(panel, gc, 6, passLabel, passField, labelSize);

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton okBtn     = UiFactory.accentButton("Connect");
        JButton cancelBtn = UiFactory.accentButton("Cancel");
        okBtn.setPreferredSize(new Dimension(120, 38));
        cancelBtn.setPreferredSize(new Dimension(100, 38));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setBackground(Theme.BG_PANEL);
        btnRow.add(okBtn);
        btnRow.add(cancelBtn);

        gc.gridx = 0; gc.gridy = 7; gc.gridwidth = 2; gc.weightx = 1;
        gc.insets = new Insets(18, 8, 4, 8);
        panel.add(btnRow, gc);

        // ── Wire actions ──────────────────────────────────────────────────────
        refreshProfileBox();

        loadBtn.addActionListener(_ -> {
            String selected = (String) profileBox.getSelectedItem();
            if (selected == null) return;
            ProfileStore.loadAll().stream()
                    .filter(p -> p.name().equals(selected))
                    .findFirst()
                    .ifPresent(this::populateFields);
        });

        saveBtn.addActionListener(_ -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter a profile name first.",
                        "Save Profile", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ProfileStore.upsert(new Profile(
                    name,
                    hostField.getText().trim(),
                    portField.getText().trim(),
                    userField.getText().trim(),
                    new String(passField.getPassword())
            ));
            refreshProfileBox();
            profileBox.setSelectedItem(name);
            JOptionPane.showMessageDialog(this, "Profile \"" + name + "\" saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });

        deleteBtn.addActionListener(_ -> {
            String selected = (String) profileBox.getSelectedItem();
            if (selected == null) return;
            int choice = JOptionPane.showConfirmDialog(this,
                    "Delete profile \"" + selected + "\"?",
                    "Delete", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                ProfileStore.delete(selected);
                refreshProfileBox();
            }
        });

        okBtn.addActionListener(_ -> onConnect());
        cancelBtn.addActionListener(_ -> showRequiredWarning());
        passField.addActionListener(_ -> onConnect()); // Enter key in password field

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                showRequiredWarning();
            }
        });

        getContentPane().add(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addRow(JPanel panel, GridBagConstraints gc, int row,
                        JLabel label, JComponent field, Dimension labelSize) {
        label.setPreferredSize(labelSize);
        gc.gridy = row; gc.gridwidth = 1;
        gc.gridx = 0; gc.weightx = 0; panel.add(label, gc);
        gc.gridx = 1; gc.weightx = 1; panel.add(field, gc);
    }

    private void refreshProfileBox() {
        String current = (String) profileBox.getSelectedItem();
        profileBox.removeAllItems();
        profileBox.addItem("— select profile —");
        ProfileStore.loadAll().forEach(p -> profileBox.addItem(p.name()));
        if (current != null) profileBox.setSelectedItem(current);
    }

    private void populateFields(Profile p) {
        nameField.setText(p.name());
        hostField.setText(p.host()     == null ? "localhost" : p.host());
        portField.setText(p.port()     == null ? "3306"      : p.port());
        userField.setText(p.username() == null ? ""          : p.username());
        passField.setText(p.password() == null ? ""          : p.password());
    }

    private void onConnect() {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String name = nameField.getText().trim();

        if (user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (host.isEmpty()) host = "localhost";
        if (port.isEmpty()) port = "3306";

        result    = new AppConfig(user, pass, host, port, name.isEmpty() ? null : name);
        confirmed = true;
        dispose();
    }

    private void showRequiredWarning() {
        JOptionPane.showMessageDialog(this,
                "A MySQL connection is required to continue.",
                "Authentication", JOptionPane.WARNING_MESSAGE);
    }

    public boolean   isConfirmed() { return confirmed; }
    public AppConfig getConfig()   { return result;    }

    // Keep old getters for compatibility
    public String getUsername() { return result != null ? result.getMysqlUser()     : ""; }
    public String getPassword() { return result != null ? result.getMysqlPassword() : ""; }
}