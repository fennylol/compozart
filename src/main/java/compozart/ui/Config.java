package compozart.ui;

import java.nio.file.Path;
import java.util.Locale;

/** Where per-user settings live. */
final class Config {
    private Config() {
    }

    static Path dir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "compozart");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "compozart");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return Path.of(xdg != null && !xdg.isBlank() ? xdg : Path.of(home, ".config").toString(), "compozart");
    }
}
