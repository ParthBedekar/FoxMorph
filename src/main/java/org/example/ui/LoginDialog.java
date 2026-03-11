package org.example.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog that collects MySQL credentials before the main window
 * becomes usable. Cannot be dismissed without valid input.
 */
public class LoginDialog extends JDialog {

    private String username;
    private String password;
    private boolean confirmed = false;

    public LoginDialog(JFrame owner) {
        super(owner, "MySQL Login", true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(480, 240));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        panel.setBackground(Theme.BG_PANEL);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(10, 10, 10, 10);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        JTextField     userField = UiFactory.accentField("");
        JPasswordField passField = UiFactory.accentPasswordField();
        JButton        okButton  = UiFactory.accentButton("OK");
        JButton        cancelBtn = UiFactory.accentButton("Cancel");

        // Explicit sizes so pack() doesn't collapse fields to near-zero
        Dimension fieldSize  = new Dimension(260, 36);
        Dimension labelSize  = new Dimension(160, 36);
        Dimension buttonSize = new Dimension(110, 38);

        userField.setPreferredSize(fieldSize);
        passField.setPreferredSize(fieldSize);
        okButton.setPreferredSize(buttonSize);
        cancelBtn.setPreferredSize(buttonSize);

        JLabel userLabel = UiFactory.bodyLabel("MySQL Username:");
        JLabel passLabel = UiFactory.bodyLabel("MySQL Password:");
        userLabel.setPreferredSize(labelSize);
        passLabel.setPreferredSize(labelSize);

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0; panel.add(userLabel, gc);
        gc.gridx = 1; gc.weightx = 1; panel.add(userField, gc);
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0; panel.add(passLabel, gc);
        gc.gridx = 1; gc.weightx = 1; panel.add(passField, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        buttons.setBackground(Theme.BG_PANEL);
        buttons.add(okButton);
        buttons.add(cancelBtn);

        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.weightx = 1;
        gc.insets = new Insets(20, 10, 4, 10);
        panel.add(buttons, gc);

        okButton.addActionListener(_ -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Username and password cannot be empty.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            username  = u;
            password  = p;
            confirmed = true;
            dispose();
        });

        cancelBtn.addActionListener(_ -> showRequiredWarning());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                showRequiredWarning();
            }
        });

        getContentPane().add(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() { return confirmed; }
    public String  getUsername() { return username;  }
    public String  getPassword() { return password;  }

    private void showRequiredWarning() {
        JOptionPane.showMessageDialog(this,
                "Login is required to continue.",
                "Authentication", JOptionPane.WARNING_MESSAGE);
    }
}