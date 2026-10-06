package compozart.ui;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the app's folders are. A built app looks like this:
 *
 * <pre>
 * compozart/
 *   compozart        run script (compozart.cmd on Windows)
 *   app/             compozart.jar and the bundled Java runtime
 *   settings/        settings.json, themes.json, icons.json, recent.json
 *   compositions/    projects saved by default, plus backups/ for copies and autosaves
 * </pre>
 */
final class AppHome {
    private AppHome() {
    }

    /**
     * The top folder. The run script names it in COMPOZART_HOME. Without the script, a jar inside an
     * {@code app} folder means the folder above it; a jar anywhere else means the jar's own folder. Running
     * from compiled classes during development uses the working directory.
     */
    static Path root() {
        String env = System.getenv("COMPOZART_HOME");
        if (env != null && !env.isBlank()) return Path.of(env).toAbsolutePath().normalize();
        try {
            Path src = Path.of(AppHome.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(src)) {
                Path dir = src.toAbsolutePath().getParent();
                return dir.getFileName() != null && dir.getFileName().toString().equals("app") ? dir.getParent() : dir;
            }
        } catch (Exception ignored) {
            // fall through to the working directory
        }
        return Path.of("").toAbsolutePath();
    }

    static Path settingsDir() {
        return root().resolve("settings");
    }

    /** Where projects are saved by default, or a per-user folder when the app's folder cannot be written to. */
    static Path compositionsDir() {
        Path p = root().resolve("compositions");
        return writable(p) ? p : Config.dir().resolve("compositions");
    }

    /** Backups and autosaves, inside the compositions folder. */
    static Path backupsDir() {
        return compositionsDir().resolve("backups");
    }

    /** True when {@code dir} exists and is writable, or can be created. */
    static boolean writable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (Exception e) {
            return false;
        }
    }
}
