package org.example.ui;

import org.example.config.AppConfig;
import org.example.converter.ConversionPipeline;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Converter tab — input fields, Generate SQL button, and live log.
 * After generation, notifies the preview panel via callback.
 */
public class ConverterPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField sourceField   = UiFactory.accentField("");
    private final JTextField destField     = UiFactory.accentField("");
    private final JTextField filenameField = UiFactory.accentField("converted.sql");

    private final JTextPane logPane    = new JTextPane();
    private final JButton   convertBtn = UiFactory.accentButton("Generate SQL →");
    private final JButton   clearBtn   = UiFactory.accentButton("Clear Log");

    private final JFrame          owner;
    private AppConfig             config;
    private java.util.function.Consumer<File> onSqlGenerated; // callback to preview panel

    public ConverterPanel(JFrame owner) {
        this.owner = owner;
        setBackground(Theme.BG_PANEL);
        setLayout(new BorderLayout(0, 0));
        add(buildFormPanel(), BorderLayout.NORTH);
        add(buildLogPanel(),  BorderLayout.CENTER);
    }

    public void setConfig(AppConfig config) {
        this.config = config;
    }

    /** Called by FoxMorph to wire the preview panel. */
    public void setOnSqlGenerated(java.util.function.Consumer<File> callback) {
        this.onSqlGenerated = callback;
    }

    // ── Form ──────────────────────────────────────────────────────────────────

    private JPanel buildFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG_PANEL);
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        addFormRow(form, gc, 0, "Source DBC Folder:", sourceField,   browseDir(sourceField));
        addFormRow(form, gc, 1, "Destination Folder:", destField,    browseDir(destField));
        addFormRow(form, gc, 2, "SQL File Name:",       filenameField, null);

        convertBtn.setPreferredSize(new Dimension(200, 40));
        clearBtn.setPreferredSize(new Dimension(120, 40));
        convertBtn.addActionListener(_ -> onGenerate());
        clearBtn.addActionListener(_ -> clearLog());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setBackground(Theme.BG_PANEL);
        btnRow.add(convertBtn);
        btnRow.add(clearBtn);

        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 3; gc.weightx = 1;
        form.add(btnRow, gc);

        return form;
    }

    private void addFormRow(JPanel form, GridBagConstraints gc, int row,
                            String labelText, JTextField field, JButton browse) {
        gc.gridy    = row;
        gc.gridwidth = 1;
        gc.gridx   = 0; gc.weightx = 0; form.add(UiFactory.bodyLabel(labelText), gc);
        gc.gridx   = 1; gc.weightx = 1; form.add(field, gc);
        if (browse != null) {
            gc.gridx = 2; gc.weightx = 0; form.add(browse, gc);
        }
    }

    private JButton browseDir(JTextField target) {
        JButton btn = UiFactory.accentButton("Browse");
        btn.setPreferredSize(new Dimension(100, 30));
        btn.addActionListener(_ -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                target.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return btn;
    }

    // ── Log panel ─────────────────────────────────────────────────────────────

    private JPanel buildLogPanel() {
        logPane.setEditable(false);
        logPane.setBackground(new Color(18, 18, 28));
        logPane.setFont(new Font("Consolas", Font.PLAIN, 13));

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.ACCENT, 1),
                "Conversion Log",
                0, 0, Theme.FONT_BODY, Theme.ACCENT));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG_PANEL);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private void appendLog(String message) {
        String line  = "[" + LocalTime.now().format(TIME_FMT) + "]  " + message + "\n";
        Color  color = colorFor(message);

        StyledDocument doc   = logPane.getStyledDocument();
        Style          style = logPane.addStyle("line", null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, "Consolas");
        StyleConstants.setFontSize(style, 13);

        try { doc.insertString(doc.getLength(), line, style); }
        catch (BadLocationException ignored) {}

        logPane.setCaretPosition(doc.getLength());
    }

    private Color colorFor(String msg) {
        if (msg.startsWith("✅") || msg.startsWith("   ✓")) return new Color(80, 220, 100);
        if (msg.startsWith("❌"))                            return new Color(255, 80,  80);
        if (msg.startsWith("⚠️"))                           return new Color(255, 180,  0);
        if (msg.startsWith("🚀"))                           return new Color(80, 180, 255);
        if (msg.startsWith("─"))                            return new Color(80,  80, 100);
        return new Color(200, 200, 210);
    }

    private void clearLog() { logPane.setText(""); }

    // ── Generation flow ───────────────────────────────────────────────────────

    private void onGenerate() {
        String source   = sourceField.getText().trim();
        String dest     = destField.getText().trim();
        String filename = filenameField.getText().trim();

        if (source.isEmpty() || dest.isEmpty() || filename.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                    "Please fill in all fields before converting.",
                    "Missing Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File[] dbcFiles = new File(source).listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".dbc"));

        if (dbcFiles == null || dbcFiles.length == 0) {
            JOptionPane.showMessageDialog(owner,
                    "No .dbc file found in the selected source folder.",
                    "Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (dbcFiles.length > 1) {
            JOptionPane.showMessageDialog(owner,
                    "Multiple .dbc files found. Please ensure only one is present.",
                    "Ambiguous Source", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String dbcPath = dbcFiles[0].getAbsolutePath();
        convertBtn.setEnabled(false);
        appendLog("Starting SQL generation...");

        new SwingWorker<File, String>() {

            @Override
            protected File doInBackground() throws Exception {
                return new ConversionPipeline(config, this::publish)
                        .generate(dbcPath, dest, filename);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(ConverterPanel.this::appendLog);
            }

            @Override
            protected void done() {
                convertBtn.setEnabled(true);
                try {
                    File sqlFile = get();
                    appendLog("─".repeat(48));
                    appendLog("✅ SQL file ready — switching to Preview tab...");
                    if (onSqlGenerated != null) onSqlGenerated.accept(sqlFile);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    appendLog("❌ Fatal error: " + cause.getMessage());
                }
            }
        }.execute();
    }
}