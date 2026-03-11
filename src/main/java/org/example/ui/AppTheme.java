package org.example.ui;

/**
 * Central CSS + colour constants for the JavaFX UI.
 */
public final class AppTheme {

    public static final String C_BG_APP      = "#f5f5f5";
    public static final String C_BG_TOPBAR   = "#ffffff";
    public static final String C_BG_SIDEBAR  = "#f0f0f0";
    public static final String C_BG_CARD     = "#ffffff";
    public static final String C_BG_INPUT    = "#ffffff";
    public static final String C_ACCENT      = "#2563eb";
    public static final String C_ACCENT_DARK = "#1d4ed8";
    public static final String C_SUCCESS     = "#16a34a";
    public static final String C_DANGER      = "#dc2626";
    public static final String C_TEXT        = "#111827";
    public static final String C_TEXT_DIM    = "#6b7280";
    public static final String C_BORDER      = "#e5e7eb";

    public static final String CSS = """
        .root {
            -fx-font-family: 'Segoe UI';
            -fx-font-size: 13px;
            -fx-background-color: #f5f5f5;
        }

        /* ── Buttons ─────────────────────────────────────── */
        .btn-primary {
            -fx-background-color: #2563eb;
            -fx-text-fill: #ffffff;
            -fx-font-weight: bold;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
            -fx-padding: 7 20 7 20;
            -fx-cursor: hand;
            -fx-border-width: 0;
        }
        .btn-primary:hover    { -fx-background-color: #1d4ed8; }
        .btn-primary:pressed  { -fx-background-color: #1e40af; }
        .btn-primary:disabled { -fx-opacity: 0.5; }

        .btn-secondary {
            -fx-background-color: #ffffff;
            -fx-text-fill: #374151;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
            -fx-padding: 7 20 7 20;
            -fx-border-color: #d1d5db;
            -fx-border-radius: 6;
            -fx-border-width: 1;
            -fx-cursor: hand;
        }
        .btn-secondary:hover   { -fx-background-color: #f9fafb; -fx-border-color: #9ca3af; }
        .btn-secondary:pressed { -fx-background-color: #f3f4f6; }

        .btn-danger {
            -fx-background-color: #ffffff;
            -fx-text-fill: #dc2626;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
            -fx-padding: 7 20 7 20;
            -fx-border-color: #fca5a5;
            -fx-border-radius: 6;
            -fx-border-width: 1;
            -fx-cursor: hand;
        }
        .btn-danger:hover { -fx-background-color: #fef2f2; -fx-border-color: #f87171; }

        /* ── Text fields ─────────────────────────────────── */
        .text-field-dark, .password-field-dark {
            -fx-background-color: #ffffff;
            -fx-text-fill: #111827;
            -fx-prompt-text-fill: #9ca3af;
            -fx-background-radius: 6;
            -fx-border-color: #d1d5db;
            -fx-border-radius: 6;
            -fx-border-width: 1;
            -fx-padding: 6 10 6 10;
        }
        .text-field-dark:focused, .password-field-dark:focused {
            -fx-border-color: #2563eb;
            -fx-border-width: 1.5;
            -fx-effect: dropshadow(gaussian, rgba(37,99,235,0.15), 4, 0, 0, 0);
        }

        /* ── ComboBox ────────────────────────────────────── */
        .combo-dark .combo-box-base {
            -fx-background-color: #ffffff;
            -fx-text-fill: #111827;
            -fx-background-radius: 6;
            -fx-border-color: #d1d5db;
            -fx-border-radius: 6;
            -fx-border-width: 1;
        }
        .combo-dark .combo-box-base:focused { -fx-border-color: #2563eb; }
        .combo-dark .list-cell             { -fx-background-color: #ffffff; -fx-text-fill: #111827; }
        .combo-dark .list-cell:hover       { -fx-background-color: #eff6ff; }
        .combo-dark .list-cell:selected    { -fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; }
        .combo-dark .arrow-button          { -fx-background-color: transparent; }
        .combo-dark .arrow                 { -fx-background-color: #6b7280; }

        /* ── Labels ──────────────────────────────────────── */
        .label-heading {
            -fx-text-fill: #111827;
            -fx-font-weight: bold;
            -fx-font-size: 14px;
        }
        .label-field {
            -fx-text-fill: #374151;
            -fx-font-size: 12px;
        }
        .label-muted {
            -fx-text-fill: #6b7280;
            -fx-font-size: 12px;
        }

        /* ── Cards ───────────────────────────────────────── */
        .card {
            -fx-background-color: #ffffff;
            -fx-background-radius: 8;
            -fx-border-color: #e5e7eb;
            -fx-border-radius: 8;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);
        }

        /* ── Sidebar nav buttons ─────────────────────────── */
        .nav-btn {
            -fx-background-color: transparent;
            -fx-text-fill: #6b7280;
            -fx-font-size: 13px;
            -fx-alignment: center-left;
            -fx-padding: 10 16 10 20;
            -fx-background-radius: 6;
            -fx-cursor: hand;
            -fx-min-width: 170;
            -fx-max-width: 170;
        }
        .nav-btn:hover { -fx-background-color: #e5e7eb; -fx-text-fill: #111827; }
        .nav-btn-active {
            -fx-background-color: #eff6ff;
            -fx-text-fill: #2563eb;
            -fx-font-weight: bold;
            -fx-border-color: transparent transparent transparent #2563eb;
            -fx-border-width: 0 0 0 3;
            -fx-padding: 10 16 10 17;
        }
        .nav-btn-active:hover { -fx-background-color: #dbeafe; }

        /* ── Scroll bars ─────────────────────────────────── */
        .scroll-bar:vertical .thumb   { -fx-background-color: #d1d5db; -fx-background-radius: 4; }
        .scroll-bar:vertical .track   { -fx-background-color: transparent; }
        .scroll-bar:vertical .increment-button,
        .scroll-bar:vertical .decrement-button { -fx-background-color: transparent; -fx-padding: 0; }
        .scroll-bar:horizontal .thumb  { -fx-background-color: #d1d5db; -fx-background-radius: 4; }
        .scroll-bar:horizontal .track  { -fx-background-color: transparent; }

        /* ── Table ───────────────────────────────────────── */
        .table-view {
            -fx-background-color: #ffffff;
            -fx-table-cell-border-color: transparent;
            -fx-border-color: #e5e7eb;
            -fx-border-width: 1;
            -fx-background-radius: 6;
            -fx-border-radius: 6;
        }
        .table-view .column-header-background {
            -fx-background-color: #f9fafb;
        }
        .table-view .column-header {
            -fx-background-color: transparent;
            -fx-text-fill: #6b7280;
            -fx-font-size: 12px;
            -fx-border-color: transparent transparent #e5e7eb transparent;
            -fx-border-width: 1;
        }
        .table-view .table-row-cell {
            -fx-background-color: #ffffff;
            -fx-text-fill: #111827;
            -fx-border-color: transparent;
            -fx-table-cell-border-color: transparent;
        }
        .table-view .table-row-cell:odd     { -fx-background-color: #f9fafb; }
        .table-view .table-row-cell:selected { -fx-background-color: #dbeafe; }
        .table-view .table-cell { -fx-text-fill: #111827; -fx-padding: 0 14 0 14; }

        /* ── Separator ───────────────────────────────────── */
        .separator .line { -fx-border-color: #e5e7eb; }

        /* ── Tab pane ────────────────────────────────────── */
        .tab-pane .tab-header-area { -fx-padding: 0; }
        .tab-pane .tab-header-background { -fx-background-color: #ffffff; }
        .tab-pane .tab {
            -fx-background-color: #ffffff;
            -fx-text-fill: #6b7280;
            -fx-padding: 6 16;
        }
        .tab-pane .tab:selected {
            -fx-background-color: #f5f5f5;
            -fx-text-fill: #111827;
            -fx-border-color: transparent transparent #2563eb transparent;
            -fx-border-width: 2;
        }
        .tab-pane .tab-content-area { -fx-background-color: #f5f5f5; }
    """;

    private AppTheme() {}
}