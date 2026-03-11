package org.example.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;

/**
 * SQL Preview tab — shows generated SQL with syntax highlighting.
 * Has its own "Run Against MySQL" button and a copy-to-clipboard button.
 */
public class SqlPreviewPanel extends JPanel {

    // SQL syntax colours (dark editor theme)
    private static final Color COL_BG         = new Color(18,  22,  38);
    private static final Color COL_KEYWORD     = new Color(86, 156, 214);   // blue
    private static final Color COL_TYPE        = new Color(78, 201, 176);   // teal
    private static final Color COL_STRING      = new Color(206, 145,  80);  // orange
    private static final Color COL_COMMENT     = new Color(106, 153,  85);  // green
    private static final Color COL_NUMBER      = new Color(181, 206, 168);  // light green
    private static final Color COL_TABLE_NAME  = new Color(220, 220, 150);  // yellow
    private static final Color COL_DEFAULT     = new Color(212, 212, 212);  // light grey
    private static final Color COL_LINENO      = new Color(80,  90, 110);
    private static final Color COL_LINENO_BG   = new Color(25,  28,  44);

    private static final Font EDITOR_FONT = new Font("Consolas", Font.PLAIN, 13);

    private final JTextPane    editor    = new JTextPane();
    private final JLabel       statusLabel;
    private final JButton      runBtn;
    private final JButton      copyBtn;
    private final Runnable     onRun;

    private File currentSqlFile;

    public SqlPreviewPanel(Runnable onRun) {
        this.onRun = onRun;
        setLayout(new BorderLayout(0, 0));
        setBackground(COL_BG);

        // Toolbar
        JPanel toolbar = buildToolbar();
        statusLabel = (JLabel) ((JPanel) toolbar.getComponent(0)).getComponent(0);
        runBtn  = findButton(toolbar, "▶  Run Against MySQL");
        copyBtn = findButton(toolbar, "⎘  Copy SQL");

        add(toolbar,       BorderLayout.NORTH);
        add(buildEditor(), BorderLayout.CENTER);

        showPlaceholder();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Load and highlight a generated SQL file. */
    public void loadFile(File sqlFile) {
        this.currentSqlFile = sqlFile;
        try {
            String sql = Files.readString(sqlFile.toPath());
            applyHighlighting(sql);
            runBtn.setEnabled(true);
            copyBtn.setEnabled(true);
            int lines = sql.split("\n").length;
            int stmts = (int) sql.chars().filter(c -> c == ';').count();
            statusLabel.setText(String.format(
                    "  %d lines  •  %d statements  •  %s",
                    lines, stmts, sqlFile.getName()));
        } catch (IOException e) {
            showError("Could not read SQL file: " + e.getMessage());
        }
    }

    /** Returns the currently loaded SQL file, or null. */
    public File getCurrentSqlFile() { return currentSqlFile; }

    /**
     * Flushes the current editor content back to the SQL file.
     * Called before execution so any edits made in the preview are used.
     */
    public void flushEditsToFile() throws Exception {
        if (currentSqlFile == null) return;
        String text = editor.getDocument().getText(0, editor.getDocument().getLength());
        java.nio.file.Files.writeString(currentSqlFile.toPath(), text);
    }

    /** Mark run button as busy/idle. */
    public void setRunning(boolean running) {
        runBtn.setEnabled(!running);
        runBtn.setText(running ? "⏳ Running..." : "▶  Run Against MySQL");
    }

    /** Show a status message (e.g. after run completes). */
    public void setStatus(String msg) {
        statusLabel.setText("  " + msg);
    }

    // ── UI builders ───────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(new Color(30, 35, 55));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                new Color(60, 70, 100)));

        // Left: status label
        JLabel status = new JLabel("  No SQL generated yet");
        status.setForeground(COL_LINENO);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        leftPanel.setOpaque(false);
        leftPanel.add(status);
        toolbar.add(leftPanel, BorderLayout.WEST);

        // Right: buttons
        JButton run  = buildToolbarButton("▶  Run Against MySQL", Theme.ACCENT);
        JButton copy = buildToolbarButton("⎘  Copy SQL", new Color(70, 90, 130));
        run.setEnabled(false);
        copy.setEnabled(false);

        run.addActionListener(_ -> { if (onRun != null) onRun.run(); });
        copy.addActionListener(_ -> copyToClipboard());

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        rightPanel.setOpaque(false);
        rightPanel.add(copy);
        rightPanel.add(run);
        toolbar.add(rightPanel, BorderLayout.EAST);

        return toolbar;
    }

    private JButton buildToolbarButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color orig = bg;
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(orig.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(orig);
            }
        });
        return btn;
    }

    private JScrollPane buildEditor() {
        editor.setEditable(true);
        editor.setBackground(COL_BG);
        editor.setFont(EDITOR_FONT);
        editor.setCaretColor(COL_DEFAULT);
        editor.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        // Line number gutter
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setRowHeaderView(new LineNumberGutter(editor));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(COL_BG);

        return scroll;
    }

    // ── Syntax highlighting ───────────────────────────────────────────────────

    private static final String[] KEYWORDS = {
            "SELECT","INSERT","UPDATE","DELETE","CREATE","DROP","ALTER","TABLE",
            "DATABASE","INDEX","UNIQUE","PRIMARY","KEY","FOREIGN","REFERENCES",
            "INTO","VALUES","FROM","WHERE","IF","NOT","EXISTS","USE","ON",
            "DEFAULT","NULL","AUTO_INCREMENT","CONSTRAINT","ADD","SET","INT",
            "VARCHAR","TEXT","DATE","DATETIME","DECIMAL","BOOLEAN","BLOB","DOUBLE"
    };

    private void applyHighlighting(String sql) {
        StyledDocument doc = new DefaultStyledDocument();
        editor.setDocument(doc);

        Style base = doc.addStyle("base", null);
        StyleConstants.setFontFamily(base, "Consolas");
        StyleConstants.setFontSize(base, 13);
        StyleConstants.setForeground(base, COL_DEFAULT);

        try {
            doc.insertString(0, sql, base);
        } catch (BadLocationException ignored) { return; }

        // Keywords
        for (String kw : KEYWORDS) {
            highlightPattern(doc, "(?i)\\b" + kw + "\\b", COL_KEYWORD, false);
        }

        // Strings
        highlightPattern(doc, "'[^']*'", COL_STRING, false);

        // Numbers
        highlightPattern(doc, "\\b\\d+(\\.\\d+)?\\b", COL_NUMBER, false);

        // Comments
        highlightPattern(doc, "--[^\n]*", COL_COMMENT, true);

        // Backtick identifiers (table/column names)
        highlightPattern(doc, "`[^`]+`", COL_TABLE_NAME, false);

        editor.setCaretPosition(0);
    }

    private void highlightPattern(StyledDocument doc, String pattern, Color color, boolean italic) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        String text;
        try { text = doc.getText(0, doc.getLength()); }
        catch (BadLocationException e) { return; }

        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            Style s = doc.addStyle(null, null);
            StyleConstants.setForeground(s, color);
            if (italic) StyleConstants.setItalic(s, true);
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, false);
        }
    }

    private void showPlaceholder() {
        StyledDocument doc = new DefaultStyledDocument();
        editor.setDocument(doc);
        Style s = doc.addStyle("ph", null);
        StyleConstants.setForeground(s, COL_LINENO);
        StyleConstants.setFontFamily(s, "Consolas");
        StyleConstants.setFontSize(s, 13);
        StyleConstants.setItalic(s, true);
        try {
            doc.insertString(0,
                    "\n\n   Generate SQL from the Converter tab to preview it here.\n\n" +
                            "   You can review and verify the output before executing it\n" +
                            "   against your MySQL database.", s);
        } catch (BadLocationException ignored) {}
    }

    private void showError(String msg) {
        StyledDocument doc = new DefaultStyledDocument();
        editor.setDocument(doc);
        Style s = doc.addStyle("err", null);
        StyleConstants.setForeground(s, new Color(255, 80, 80));
        StyleConstants.setFontFamily(s, "Consolas");
        StyleConstants.setFontSize(s, 13);
        try { doc.insertString(0, msg, s); }
        catch (BadLocationException ignored) {}
    }

    private void copyToClipboard() {
        try {
            String text = editor.getDocument().getText(0, editor.getDocument().getLength());
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            String prev = statusLabel.getText();
            statusLabel.setText("  ✓ Copied to clipboard!");
            Timer t = new Timer(2000, _ -> statusLabel.setText(prev));
            t.setRepeats(false);
            t.start();
        } catch (BadLocationException ignored) {}
    }

    private JButton findButton(JPanel toolbar, String text) {
        for (Component c : toolbar.getComponents()) {
            if (c instanceof JPanel p) {
                for (Component cc : p.getComponents()) {
                    if (cc instanceof JButton b && b.getText().equals(text)) return b;
                }
            }
        }
        return new JButton(); // fallback, should never happen
    }

    // ── Line number gutter ────────────────────────────────────────────────────

    private static class LineNumberGutter extends JComponent {
        private final JTextPane editor;

        LineNumberGutter(JTextPane editor) {
            this.editor = editor;
            setPreferredSize(new Dimension(48, 0));
            setBackground(COL_LINENO_BG);
            setFont(new Font("Consolas", Font.PLAIN, 12));
            editor.getDocument().addDocumentListener(
                    new javax.swing.event.DocumentListener() {
                        public void insertUpdate(javax.swing.event.DocumentEvent e)  { repaint(); }
                        public void removeUpdate(javax.swing.event.DocumentEvent e)  { repaint(); }
                        public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                    });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(COL_LINENO_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());

            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());

            Rectangle clip = g.getClipBounds();
            int startOffset = editor.viewToModel2D(new Point(0, clip.y));
            int endOffset   = editor.viewToModel2D(new Point(0, clip.y + clip.height));

            Element root = editor.getDocument().getDefaultRootElement();
            int startLine = root.getElementIndex(startOffset);
            int endLine   = root.getElementIndex(endOffset);

            for (int i = startLine; i <= endLine; i++) {
                Element elem = root.getElement(i);
                int y;
                try {
                    Rectangle r = editor.modelToView2D(elem.getStartOffset()).getBounds();
                    y = r.y + r.height - fm.getDescent();
                } catch (BadLocationException e) { continue; }

                String num = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(num) - 6;
                g2.setColor(COL_LINENO);
                g2.drawString(num, x, y);
            }
        }
    }
}