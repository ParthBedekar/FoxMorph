package org.example.ui;

import java.awt.*;

/**
 * Central palette and font definitions so every UI component stays consistent.
 */
public final class Theme {

    public static final Color BG_DARK       = new Color(20, 30, 60);
    public static final Color BG_TOPBAR     = new Color(30, 40, 80);
    public static final Color BG_BUTTON     = new Color(50, 70, 120);
    public static final Color BG_BUTTON_HOV = new Color(255, 165, 0);
    public static final Color BG_PANEL      = new Color(245, 245, 250);
    public static final Color ACCENT        = new Color(255, 165, 0);
    public static final Color TEXT_DARK     = new Color(50, 70, 120);

    public static final Font FONT_TITLE  = new Font("Segoe UI Light", Font.PLAIN, 36);
    public static final Font FONT_BODY   = new Font("Segoe UI",       Font.PLAIN, 16);
    public static final Font FONT_BUTTON = new Font("Segoe UI",       Font.BOLD,  16);
    public static final Font FONT_FIELD  = new Font("Segoe UI",       Font.PLAIN, 14);

    private Theme() {}
}
