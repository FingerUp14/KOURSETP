package sync.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import sync.controller.SyncController;
import sync.model.DirectorySnapshot;
import sync.util.AppLogger;

public class MainWindow extends JFrame {

    private SyncController controller;

    private JTextField directoryField;
    private JTextField ipField;
    private JTextField remotePortField;
    private JTextField localPortField;

    private JButton browseButton;
    private JButton connectButton;
    private JButton syncButton;

    private JTextArea logArea;

    private JLabel statusLabel;

    public MainWindow() {

        setTitle("File Synchronizer");

        setSize(850, 600);

        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        createInterface();

        registerListeners();

        AppLogger.setLogArea(logArea);

        AppLogger.log("Application started.");

    }

    private void createInterface() {

        JPanel root = new JPanel(new BorderLayout(10, 10));

        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        add(root);

        JPanel top = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.insets = new Insets(5, 5, 5, 5);

        c.fill = GridBagConstraints.HORIZONTAL;

        directoryField = new JTextField();

        browseButton = new JButton("Browse");

        ipField = new JTextField("127.0.0.1");

        remotePortField = new JTextField("5001");

        localPortField = new JTextField("5000");

        addRow(top, c, 0, "Folder:", directoryField, browseButton);

        addRow(top, c, 1, "IP:", ipField, null);

        addRow(top, c, 2, "Remote Port:", remotePortField, null);

        addRow(top, c, 3, "Local Port:", localPortField, null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));

        connectButton = new JButton("Connect");

        syncButton = new JButton("Synchronize");

        connectButton.setEnabled(false);

        Dimension buttonSize = new Dimension(150, 35);

        connectButton.setPreferredSize(buttonSize);

        syncButton.setPreferredSize(buttonSize);

        buttons.add(connectButton);

        buttons.add(syncButton);

        logArea = new JTextArea();

        logArea.setEditable(false);

        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(logArea);

        statusLabel = new JLabel("Ready");

        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        root.add(top, BorderLayout.NORTH);

        root.add(buttons, BorderLayout.CENTER);

        root.add(scrollPane, BorderLayout.SOUTH);

        add(statusLabel, BorderLayout.SOUTH);

    }

    private void addRow(
            JPanel panel,
            GridBagConstraints c,
            int row,
            String label,
            JTextField field,
            JButton button) {

        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;

        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;

        panel.add(field, c);

        if (button != null) {

            c.gridx = 2;
            c.weightx = 0;

            panel.add(button, c);

        }

    }
        private void registerListeners() {

        browseButton.addActionListener(e -> chooseDirectory());

        syncButton.addActionListener(e -> synchronize());

    }

    private void chooseDirectory() {

        JFileChooser chooser = new JFileChooser();

        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

            File folder = chooser.getSelectedFile();

            directoryField.setText(folder.getAbsolutePath());

            statusLabel.setText("Folder selected");

            AppLogger.log("Folder selected: " + folder.getAbsolutePath());

        }

    }

    private void synchronize() {

        if (directoryField.getText().isBlank()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please select a folder."
            );

            return;

        }

        try {

            if (controller == null) {

                int localPort =
                        Integer.parseInt(localPortField.getText());

                controller = new SyncController(localPort);

            }

            statusLabel.setText("Synchronizing...");

            DirectorySnapshot remoteSnapshot =
                    controller.synchronize(
                            new File(directoryField.getText()),
                            ipField.getText(),
                            Integer.parseInt(remotePortField.getText())
                    );

            if (remoteSnapshot != null) {

                AppLogger.log(
                        "Remote snapshot received. Files: "
                                + remoteSnapshot.size()
                );

                statusLabel.setText("Synchronization completed");

            } else {

                statusLabel.setText("Synchronization failed");

            }

        } catch (Exception e) {

            statusLabel.setText("Error");

            AppLogger.log("Synchronization error: " + e.getMessage());

            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );

        }

    }

}