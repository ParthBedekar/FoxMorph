package foxql;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;

public class FoxMorph {
    private String filename;
    private String sourceFolder;
    private String destFolder;

    public FoxMorph() {
        JFrame frame = new JFrame("FoxMorph");
        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel topBar = new JPanel();
        topBar.setBackground(new Color(30, 40, 80));
        topBar.setPreferredSize(new Dimension(frame.getWidth(), 80));
        topBar.setLayout(new FlowLayout(FlowLayout.LEFT, 30, 20));

        JLabel titleLabel = new JLabel("FoxMorph");
        titleLabel.setFont(new Font("Segoe UI Light", Font.PLAIN, 36));
        titleLabel.setForeground(new Color(255, 165, 0)); // Orange accent
        topBar.add(titleLabel);

        frame.add(topBar, BorderLayout.NORTH);

        JPanel leftSidebar = new JPanel();
        leftSidebar.setBackground(new Color(20, 30, 60));
        leftSidebar.setPreferredSize(new Dimension(200, frame.getHeight()));
        leftSidebar.setLayout(new BoxLayout(leftSidebar, BoxLayout.Y_AXIS));

        JButton converterButton = new JButton("Converter");
        converterButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        converterButton.setMaximumSize(new Dimension(180, 40));
        converterButton.setBackground(new Color(50, 70, 120));
        converterButton.setForeground(new Color(255, 165, 0)); // Orange accent text
        converterButton.setFocusPainted(false);
        converterButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        converterButton.setBorder(new LineBorder(new Color(255, 165, 0), 2));
        converterButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                converterButton.setBackground(new Color(255, 165, 0));
                converterButton.setForeground(new Color(50, 70, 120));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                converterButton.setBackground(new Color(50, 70, 120));
                converterButton.setForeground(new Color(255, 165, 0));
            }
        });

        leftSidebar.add(Box.createVerticalStrut(50));
        leftSidebar.add(converterButton);

        frame.add(leftSidebar, BorderLayout.WEST);

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(new Color(245, 245, 250));
        mainPanel.setLayout(null);

        JLabel sourceLabel = new JLabel("Source DBC Folder:");
        sourceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        mainPanel.add(sourceLabel);
        sourceLabel.setBounds(50, 50, 200, 25);

        JTextField sourceField = new JTextField();
        sourceField.setBounds(250, 50, 400, 30);
        sourceField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sourceField.setBorder(new LineBorder(new Color(255, 165, 0), 2)); // Orange accent border
        mainPanel.add(sourceField);

        JButton sourceBrowse = new JButton("Browse");
        sourceBrowse.setBounds(670, 50, 100, 30);
        sourceBrowse.setBackground(new Color(50, 70, 120));
        sourceBrowse.setForeground(new Color(255, 165, 0)); // Orange text
        sourceBrowse.setFocusPainted(false);
        mainPanel.add(sourceBrowse);

        JLabel destLabel = new JLabel("Destination Folder:");
        destLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        destLabel.setBounds(50, 110, 200, 25);
        mainPanel.add(destLabel);

        JTextField destField = new JTextField();
        destField.setBounds(250, 110, 400, 30);
        destField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        destField.setBorder(new LineBorder(new Color(255, 165, 0), 2));
        mainPanel.add(destField);

        JButton destBrowse = new JButton("Browse");
        destBrowse.setBounds(670, 110, 100, 30);
        destBrowse.setBackground(new Color(50, 70, 120));
        destBrowse.setForeground(new Color(255, 165, 0));
        destBrowse.setFocusPainted(false);
        mainPanel.add(destBrowse);

        JLabel fileLabel = new JLabel("SQL File Name:");
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        fileLabel.setBounds(50, 170, 200, 25);
        mainPanel.add(fileLabel);

        JTextField fileField = new JTextField("converted.sql");
        fileField.setBounds(250, 170, 400, 30);
        fileField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fileField.setBorder(new LineBorder(new Color(255, 165, 0), 2));
        mainPanel.add(fileField);

        JButton convertButton = new JButton("Convert to SQL");
        convertButton.setBounds(350, 240, 180, 40);
        convertButton.setBackground(new Color(50, 90, 140));
        convertButton.setForeground(new Color(255, 165, 0));
        convertButton.setFocusPainted(false);
        convertButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        convertButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                convertButton.setBackground(new Color(255, 165, 0));
                convertButton.setForeground(new Color(50, 70, 120));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                convertButton.setBackground(new Color(50, 90, 140));
                convertButton.setForeground(new Color(255, 165, 0));
            }
        });
        mainPanel.add(convertButton);

        sourceBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                sourceFolder = chooser.getSelectedFile().getAbsolutePath();
                sourceField.setText(sourceFolder);
            }
        });

        destBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                destFolder = chooser.getSelectedFile().getAbsolutePath();
                destField.setText(destFolder);
            }
        });

        convertButton.addActionListener(e -> {
            filename = fileField.getText().trim();
            if (sourceFolder != null && destFolder != null && !filename.isEmpty()) {
                File[] dbcFiles = new File(sourceFolder).listFiles((dir, name) -> name.toLowerCase().endsWith(".dbc"));
                if (dbcFiles != null && dbcFiles.length == 1) {
                    File dbcFile = dbcFiles[0];
                    File destFile = new File(destFolder, filename);
                    Converter converter = new Converter(destFolder, filename);
                    converter.read(dbcFile.getAbsolutePath(), destFile);
                    JOptionPane.showMessageDialog(frame, "Conversion completed!");
                } else {
                    JOptionPane.showMessageDialog(frame, "No DBC file found in selected source folder.");
                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please select source folder, destination folder and enter file name.");
            }
        });

        frame.add(mainPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        new FoxMorph();
    }
}
