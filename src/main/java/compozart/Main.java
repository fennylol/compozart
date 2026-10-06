package compozart;

import compozart.io.ProjectIO;
import compozart.model.Project;
import compozart.ui.MainWindow;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String appearanceProblems = compozart.ui.Appearance.get().loadAndApply();
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPane.dividerSize", 6);

        SwingUtilities.invokeLater(() -> {
            // Without a file to open, start at the home screen.
            if (args.length == 0) {
                compozart.ui.HomeWindow home = new compozart.ui.HomeWindow();
                home.setVisible(true);
                return;
            }
            Project project = Project.createDefault();
            Path file = null;
            String error = null;
            Path f = Path.of(args[0]);
            if (Files.exists(f)) {
                try {
                    project = ProjectIO.load(f);
                    file = f;
                } catch (Exception e) {
                    error = "Could not open " + f + ":\n" + e.getMessage();
                }
            } else {
                error = f + " does not exist.";
            }
            MainWindow w = new MainWindow(project, file);
            w.setVisible(true);
            if (error != null) JOptionPane.showMessageDialog(w, error, "Open", JOptionPane.ERROR_MESSAGE);
            if (appearanceProblems != null) w.showStatus(appearanceProblems);
        });
    }
}
