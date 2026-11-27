package sync;

import javax.swing.SwingUtilities;
import sync.view.MainWindow;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            MainWindow window = new MainWindow();
            window.setVisible(true);

        });

    }

}