package org.example.ui;

import org.example.config.AppConfig;
import org.example.converter.ConversionPipeline;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class FoxMorph {

    private final JFrame          frame;
    private final ConverterPanel  converterPanel;
    private final SqlPreviewPanel previewPanel;
    private final HistoryPanel    historyPanel;
    private final JTabbedPane     tabs;
    private AppConfig             config;

    public FoxMorph() {
        frame = new JFrame("FoxMorph");
        frame.setSize(1100, 750);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.add(buildTopBar(),  BorderLayout.NORTH);
        frame.add(buildSidebar(), BorderLayout.WEST);

        tabs           = new JTabbedPane();
        converterPanel = new ConverterPanel(frame);
        previewPanel   = new SqlPreviewPanel(this::onRunSql);
        historyPanel   = new HistoryPanel();

        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.addTab("⚙  Converter",  converterPanel);
        tabs.addTab("🔍  SQL Preview", previewPanel);
        tabs.addTab("📋  History",    historyPanel);

        // When SQL is generated → load preview → switch to preview tab
        converterPanel.setOnSqlGenerated(sqlFile -> {
            previewPanel.loadFile(sqlFile);
            tabs.setSelectedIndex(1);
        });

        frame.add(tabs, BorderLayout.CENTER);
        frame.setVisible(true);

        LoginDialog login = new LoginDialog(frame);
        login.setVisible(true);

        if (login.isConfirmed()) {
            config = login.getConfig();
            converterPanel.setConfig(config);
            updateTopBarProfile(config.getProfileName());
        } else {
            System.exit(0);
        }
    }

    // ── Run SQL handler ───────────────────────────────────────────────────────

    private void onRunSql() {
        File sqlFile = previewPanel.getCurrentSqlFile();
        if (sqlFile == null || !sqlFile.exists()) return;

        previewPanel.setRunning(true);

        new SwingWorker<Void, Void>() {
            String statusMsg = "";
            @Override
            protected Void doInBackground() throws Exception {
                previewPanel.flushEditsToFile();
                new ConversionPipeline(config).execute(sqlFile);
                statusMsg = "✅ Executed successfully against MySQL";
                return null;
            }
            @Override
            protected void done() {
                previewPanel.setRunning(false);
                try {
                    get();
                    previewPanel.setStatus(statusMsg);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    previewPanel.setStatus("❌ " + cause.getMessage());
                }
                historyPanel.refresh(); // update history tab after run
            }
        }.execute();
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private JLabel profileLabel;

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_TOPBAR);
        bar.setPreferredSize(new Dimension(frame.getWidth(), 72));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 24));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        left.setOpaque(false);

        JLabel title = new JLabel("FoxMorph");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT);

        JLabel sub = new JLabel("Visual FoxPro → MySQL");
        sub.setFont(new Font("Segoe UI Light", Font.PLAIN, 15));
        sub.setForeground(new Color(170, 180, 210));

        left.add(title);
        left.add(sub);

        // Right: active profile chip
        profileLabel = new JLabel();
        profileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        profileLabel.setForeground(new Color(160, 170, 200));
        profileLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        bar.add(left,          BorderLayout.WEST);
        bar.add(profileLabel,  BorderLayout.EAST);

        return bar;
    }

    private void updateTopBarProfile(String name) {
        if (name != null && !name.isEmpty()) {
            profileLabel.setText("🔌 " + name);
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Theme.BG_DARK);
        sidebar.setPreferredSize(new Dimension(200, frame.getHeight()));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        sidebar.add(Box.createVerticalStrut(40));
        sidebar.add(navBtn("⚙  Converter", 0));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navBtn("🔍  Preview",  1));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(navBtn("📋  History",  2));

        return sidebar;
    }

    private JButton navBtn(String text, int tabIndex) {
        JButton btn = UiFactory.accentButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(180, 40));
        btn.addActionListener(_ -> {
            tabs.setSelectedIndex(tabIndex);
            if (tabIndex == 2) historyPanel.refresh();
        });
        return btn;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FoxMorph::new);
    }
}