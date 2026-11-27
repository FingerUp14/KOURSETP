package sync.view;

import sync.controller.SyncController;
import sync.util.AppLogger;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class MainWindow extends JFrame {
    private final JTextField directory = new JTextField();
    private final JTextField host = new JTextField("127.0.0.1");
    private final JTextField localPort = new JTextField("5000");
    private final JTextField remotePort = new JTextField("5001");
    private final JTextField interval = new JTextField("10");
    private final JTextArea journal = new JTextArea();
    private final JLabel status = new JLabel("Укажите параметры и запустите синхронизацию");
    private final JButton browse = new JButton("Обзор...");
    private final JButton start = new JButton("Подключиться");
    private final JButton synchronize = new JButton("Синхронизировать");
    private final JButton stop = new JButton("Остановить");
    private volatile SyncController controller;
    private AppLogger log;

    public MainWindow() {
        super("Синхронизация файлов");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(850, 570);
        setMinimumSize(new java.awt.Dimension(680, 470));
        setLocationRelativeTo(null);
        buildInterface();
        setRunning(false);
        browse.addActionListener(event -> chooseDirectory());
        start.addActionListener(event -> start());
        synchronize.addActionListener(event -> synchronize());
        stop.addActionListener(event -> stop());
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        stop();
                        dispose();
                    }
                });
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setContentPane(root);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, constraints, 0, "Каталог:", directory, browse);
        addRow(form, constraints, 1, "Адрес второго компьютера:", host, null);
        addRow(form, constraints, 2, "Локальный порт:", localPort, null);
        addRow(form, constraints, 3, "Порт второго компьютера:", remotePort, null);
        addRow(form, constraints, 4, "Интервал, секунд:", interval, null);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(start);
        buttons.add(synchronize);
        buttons.add(stop);
        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);
        journal.setEditable(false);
        journal.setLineWrap(true);
        journal.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(journal);
        scroll.setBorder(BorderFactory.createTitledBorder("Журнал работы"));
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
    }

    private void addRow(
            JPanel panel,
            GridBagConstraints c,
            int row,
            String title,
            JTextField field,
            JButton button) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel(title), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
        if (button != null) {
            c.gridx = 2;
            c.weightx = 0;
            panel.add(button, c);
        }
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directory.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void start() {
        try {
            if (directory.getText().isBlank()) {
                throw new IllegalArgumentException("Выберите каталог");
            }
            int local = Integer.parseInt(localPort.getText().trim());
            Path logPath = Path.of("sync.log");
            log =
                    new AppLogger(
                            logPath,
                            line ->
                                    SwingUtilities.invokeLater(
                                            () -> {
                                                journal.append(line + "\n");
                                                journal.setCaretPosition(
                                                        journal.getDocument().getLength());
                                                if (line.contains("Завершено:")) {
                                                    status.setText(
                                                            line.substring(
                                                                    line.indexOf("Завершено:")));
                                                }
                                            }));
            SyncController next =
                    new SyncController(
                            Path.of(directory.getText().trim()),
                            local,
                            host.getText(),
                            Integer.parseInt(remotePort.getText().trim()),
                            Integer.parseInt(interval.getText().trim()),
                            log);
            next.start(false);
            controller = next;
            setRunning(true);
            synchronize.setEnabled(false);
            status.setText("Ожидание подключения к партнёру...");
            Thread connector =
                    new Thread(
                            () -> {
                                int failures = 0;
                                while (!Thread.currentThread().isInterrupted()) {
                                    try {
                                        next.connect();
                                        SwingUtilities.invokeLater(
                                                () -> {
                                                    if (controller == next) {
                                                        synchronize.setEnabled(true);
                                                        status.setText(
                                                                "Подключено. Журнал: "
                                                                        + log.getFile());
                                                    }
                                                });
                                        return;
                                    } catch (Exception e) {
                                        if (controller != next) {
                                            return;
                                        }
                                        log.log(
                                                "Ошибка подключения: "
                                                        + e.getMessage()
                                                        + "; повторная попытка");
                                        if (++failures == 3) {
                                            SwingUtilities.invokeLater(
                                                    () -> {
                                                        if (controller == next
                                                                && !synchronize.isEnabled()) {
                                                            JOptionPane.showMessageDialog(
                                                                    this,
                                                                    "Партнёр недоступен: "
                                                                            + e.getMessage()
                                                                            + ". Попытки"
                                                                            + " подключения"
                                                                            + " продолжаются.",
                                                                    "Ошибка подключения",
                                                                    JOptionPane.ERROR_MESSAGE);
                                                        }
                                                    });
                                        }
                                        try {
                                            Thread.sleep(2000);
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            return;
                                        }
                                    }
                                }
                            },
                            "connect-peer");
            connector.setDaemon(true);
            connector.start();
        } catch (Exception e) {
            showError(e);
        }
    }

    private void synchronize() {
        SyncController current = controller;
        if (current == null) {
            return;
        }
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                String result = current.synchronizeNow();
                                current.startAutomatic();
                                SwingUtilities.invokeLater(() -> status.setText(result));
                            } catch (Exception e) {
                                log.log("Ошибка синхронизации: " + e.getMessage());
                                SwingUtilities.invokeLater(
                                        () -> {
                                            status.setText("Ошибка: " + e.getMessage());
                                            JOptionPane.showMessageDialog(
                                                    this,
                                                    e.getMessage(),
                                                    "Ошибка синхронизации",
                                                    JOptionPane.ERROR_MESSAGE);
                                        });
                            }
                        },
                        "manual-sync");
        worker.setDaemon(true);
        worker.start();
    }

    private void stop() {
        if (controller != null) {
            try {
                controller.close();
            } catch (Exception e) {
                showError(e);
            }
            controller = null;
        }
        setRunning(false);
        status.setText("Остановлено");
    }

    private void setRunning(boolean running) {
        directory.setEnabled(!running);
        host.setEnabled(!running);
        localPort.setEnabled(!running);
        remotePort.setEnabled(!running);
        interval.setEnabled(!running);
        browse.setEnabled(!running);
        start.setEnabled(!running);
        synchronize.setEnabled(running);
        stop.setEnabled(running);
    }

    private void showError(Exception e) {
        status.setText("Ошибка");
        JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
    }
}
