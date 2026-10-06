package compozart;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import compozart.io.ProjectIO;
import compozart.model.Project;
import compozart.ui.MainWindow;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        // Catppuccin mauve as the accent, to match the default palette.
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#cba6f7"));
        FlatDarkLaf.setup();
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPane.dividerSize", 6);

        SwingUtilities.invokeLater(() -> {
            Project project = Project.createDefault();
            Path file = null;
            String error = null;
            if (args.length > 0) {
                Path f = Path.of(args[0]);
                if (Files.exists(f)) {
                    try {
                        project = ProjectIO.load(f);
                        file = f;
                    } catch (Exception e) {
                        error = "Could not open " + f + ":\n" + e.getMessage();
                    }
                }
            }
            MainWindow w = new MainWindow(project, file);
            w.setVisible(true);
            if (error != null) JOptionPane.showMessageDialog(w, error, "Open", JOptionPane.ERROR_MESSAGE);
        });
    }
}
