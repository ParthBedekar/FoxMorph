package org.example.ui;

import org.example.config.AppConfig;
import org.example.converter.ConversionPipeline;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class FoxMorph {

    private final JFrame          frame;
    private final ConverterPanel  converterPanel;
    private final SqlPreviewPanel previewPanel;
    private final JTabbedPane     tabs;
    private AppConfig             config;

    public FoxMorph() {
        frame = new JFrame("FoxMorph");
        frame.setSize(1100, 750);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.add(buildTopBar(),  BorderLayout.NORTH);
        frame.add(buildSidebar(), BorderLayout.WEST);

        // Tabs
        tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.setBackground(Theme.BG_PANEL);
        tabs.setForeground(Theme.TEXT_DARK);

        converterPanel = new ConverterPanel(frame);
        previewPanel   = new SqlPreviewPanel(this::onRunSql);

        tabs.addTab("⚙  Converter", converterPanel);
        tabs.addTab("🔍  SQL Preview", previewPanel);

        // Wire: when SQL is generated, load it in preview and switch tabs
        converterPanel.setOnSqlGenerated(sqlFile -> {
            previewPanel.loadFile(sqlFile);
            tabs.setSelectedIndex(1);
        });

        frame.add(tabs, BorderLayout.CENTER);
        frame.setVisible(true);

        LoginDialog login = new LoginDialog(frame);
        login.setVisible(true);

        if (login.isConfirmed()) {
            config = new AppConfig(login.getUsername(), login.getPassword());
            converterPanel.setConfig(config);
        } else {
            System.exit(0);
        }
    }

    /** Called by SqlPreviewPanel's "Run Against MySQL" button. */
    private void onRunSql() {
        File sqlFile = previewPanel.getCurrentSqlFile();
        if (sqlFile == null || !sqlFile.exists()) return;

        previewPanel.setRunning(true);

        new SwingWorker<Void, Void>() {
            String result = "";
            @Override
            protected Void doInBackground() throws Exception {
                previewPanel.flushEditsToFile(); // save any edits before running
                new ConversionPipeline(config).execute(sqlFile);
                result = "✅ Executed successfully against MySQL";
                return null;
            }
            @Override
            protected void done() {
                previewPanel.setRunning(false);
                try {
                    get();
                    previewPanel.setStatus(result);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    previewPanel.setStatus("❌ " + cause.getMessage());
                }
            }
        }.execute();
    }

    // ── Chrome ────────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 30, 20));
        bar.setBackground(Theme.BG_TOPBAR);
        bar.setPreferredSize(new Dimension(frame.getWidth(), 80));

        JLabel title = new JLabel("FoxMorph");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT);
        bar.add(title);

        JLabel subtitle = new JLabel("Visual FoxPro → MySQL");
        subtitle.setFont(new Font("Segoe UI Light", Font.PLAIN, 16));
        subtitle.setForeground(new Color(180, 180, 200));
        bar.add(subtitle);

        return bar;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Theme.BG_DARK);
        sidebar.setPreferredSize(new Dimension(200, frame.getHeight()));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JButton converterBtn = makeNavButton("⚙  Converter", 0);
        JButton previewBtn   = makeNavButton("🔍  Preview",  1);

        sidebar.add(Box.createVerticalStrut(50));
        sidebar.add(converterBtn);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(previewBtn);

        return sidebar;
    }

    private JButton makeNavButton(String text, int tabIndex) {
        JButton btn = UiFactory.accentButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(180, 40));
        btn.addActionListener(_ -> tabs.setSelectedIndex(tabIndex));
        return btn;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FoxMorph::new);
    }
}