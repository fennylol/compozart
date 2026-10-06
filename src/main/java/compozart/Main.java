package compozart;

import compozart.io.ProjectIO;
import compozart.model.Project;
import compozart.text.L10n;
import compozart.ui.MainWindow;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String appearanceProblems = compozart.ui.Startup.prepare();
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
                    error = L10n.t("dialog.open.error", "file", f, "reason", e.getMessage());
                }
            } else {
                error = L10n.t("dialog.open.missing", "file", f);
            }
            MainWindow w = new MainWindow(project, file);
            w.setVisible(true);
            if (error != null) JOptionPane.showMessageDialog(w, error, L10n.t("dialog.open.errorTitle"), JOptionPane.ERROR_MESSAGE);
            if (appearanceProblems != null) w.showStatus(appearanceProblems);
        });
    }
}
