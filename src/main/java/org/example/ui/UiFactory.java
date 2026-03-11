package org.example.ui;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Factory helpers that produce consistently styled Swing components.
 */
public final class UiFactory {

    private UiFactory() {}

    public static JButton accentButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(Theme.BG_BUTTON);
        btn.setForeground(Theme.ACCENT);
        btn.setFocusPainted(false);
        btn.setFont(Theme.FONT_BUTTON);
        btn.setBorder(new LineBorder(Theme.ACCENT, 2));
        addHoverEffect(btn);
        return btn;
    }

    public static JTextField accentField(String placeholder) {
        JTextField field = new JTextField(placeholder);
        field.setFont(Theme.FONT_FIELD);
        field.setBorder(new LineBorder(Theme.ACCENT, 2));
        return field;
    }

    public static JPasswordField accentPasswordField() {
        JPasswordField field = new JPasswordField();
        field.setFont(Theme.FONT_FIELD);
        field.setBorder(new LineBorder(Theme.ACCENT, 2));
        return field;
    }

    public static JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_BODY);
        return label;
    }

    /** Adds a standard orange-hover color swap to a button. */
    public static void addHoverEffect(JButton btn) {
        Color normalBg = btn.getBackground();
        Color normalFg = btn.getForeground();
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(Theme.BG_BUTTON_HOV);
                btn.setForeground(Theme.TEXT_DARK);
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(normalBg);
                btn.setForeground(normalFg);
            }
        });
    }
}
