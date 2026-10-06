package compozart.ui;

import compozart.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code settings.json}, kept in the app's settings folder so it can be edited by hand.
 * If that folder is not writable, the per-user config folder is used instead.
 */
final class Settings {
    static final String FILE_NAME = "settings.json";
    private static final int VERSION = 4;

    /** Actions whose id changed between settings versions: their saved keys move to the new id. */
    private static final Map<Integer, Map<String, String>> RENAMED = Map.of(
            2, Map.of("tool.pencil", "tool.draw"));

    /**
     * Keys that changed default between settings versions. A file from before the change that still holds the
     * old default gets the new default; an action the user had customized keeps their keys.
     */
    private static final Map<Integer, Map<String, List<String>>> OLD_DEFAULTS = Map.of(
            1, Map.of(
                    "node.new", List.of(),
                    "node.duplicate", List.of(),
                    "node.resize", List.of()),
            3, Map.of(
                    "node.edit", List.of()));

    private final Path primary;
    private Path loadedFrom;
    private long loadedModified;

    /** How many timestamped backups to keep per project. */
    int backupKeep = 20;
    /** Minutes between autosaves of unsaved changes into the backups folder; 0 turns autosave off. */
    int autosaveMinutes = 5;
    /** How many recent projects the home screen lists. */
    int recentProjects = 10;

    Settings() {
        this(AppHome.settingsDir().resolve(FILE_NAME));
    }

    /** Settings stored at {@code primary}, falling back to the user config folder. */
    Settings(Path primary) {
        this.primary = primary;
    }

    static Path fallback() {
        return Config.dir().resolve(FILE_NAME);
    }

    /** The file in use, or where it will be written. */
    Path path() {
        return loadedFrom != null ? loadedFrom : primary;
    }

    /**
     * Reads the settings into the key map. Writes a complete default file when none exists, so there is
     * something to edit.
     *
     * @return problems worth showing in the status bar, or null
     */
    String load(KeyMap keys) {
        Path f = Files.exists(primary) ? primary : Files.exists(fallback()) ? fallback() : null;
        // Without a key map (the home screen) only the plain options are read, and nothing is written.
        if (keys == null) return f == null ? null : read(null, f);
        if (f == null) {
            try {
                save(keys);
            } catch (IOException e) {
                return "Could not write " + FILE_NAME + ": " + e.getMessage();
            }
            return null;
        }
        return read(keys, f);
    }

    /** Reloads when the file changed on disk since it was last read or written. */
    String reloadIfChanged(KeyMap keys) {
        Path f = path();
        try {
            if (!Files.exists(f) || Files.getLastModifiedTime(f).toMillis() == loadedModified) return null;
        } catch (IOException e) {
            return null;
        }
        String problems = read(keys, f);
        return problems != null ? problems : "Reloaded " + f;
    }

    private String read(KeyMap keys, Path f) {
        try {
            loadedFrom = f;
            loadedModified = Files.getLastModifiedTime(f).toMillis();
            Map<String, Object> m = Json.obj(Json.parse(Files.readString(f, StandardCharsets.UTF_8)), FILE_NAME);
            Object backups = m.get("backups");
            if (backups != null) {
                Map<String, Object> b = Json.obj(backups, "backups");
                backupKeep = (int) Math.max(0, Json.num(b, "keep", backupKeep));
                autosaveMinutes = (int) Math.max(0, Json.num(b, "autosaveMinutes", autosaveMinutes));
            }
            recentProjects = (int) Math.max(1, Json.num(m, "recentProjects", recentProjects));
            if (keys == null) return null;
            Object kb = m.get("keybindings");
            if (kb == null) return null;
            Map<String, Object> bindings = new LinkedHashMap<>(Json.obj(kb, "keybindings"));
            long version = Json.num(m, "version", 1);
            for (int v = (int) version; v < VERSION; v++) {
                for (var old : OLD_DEFAULTS.getOrDefault(v, Map.of()).entrySet()) {
                    if (old.getValue().equals(bindings.get(old.getKey()))) bindings.remove(old.getKey());
                }
                for (var r : RENAMED.getOrDefault(v, Map.of()).entrySet()) {
                    Object keys0 = bindings.remove(r.getKey());
                    if (keys0 != null) bindings.putIfAbsent(r.getValue(), keys0);
                }
            }
            List<String> problems = new ArrayList<>(keys.fromJson(bindings));
            if (version < VERSION) {
                try {
                    save(keys);
                } catch (IOException e) {
                    problems.add("could not upgrade the file: " + e.getMessage());
                }
            }
            return problems.isEmpty() ? null : f.getFileName() + ": " + String.join("; ", problems);
        } catch (IOException | RuntimeException e) {
            return "Could not read " + f + ": " + e.getMessage();
        }
    }

    /** Writes every setting, defaults included. Falls back to the user config folder if the program folder is read-only. */
    Path save(KeyMap keys) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("about", "compozart settings. Edit freely; the app reloads this file when its window regains focus. "
                + "Keys are written like \"ctrl+shift+Z\". Key names are Java KeyEvent names without VK_, "
                + "for example COMMA, OPEN_BRACKET, DELETE, F5. An empty list leaves an action unbound. "
                + "Backups: keep is how many timestamped copies to keep per project; autosaveMinutes 0 turns autosave off.");
        m.put("version", (long) VERSION);
        Map<String, Object> backups = new LinkedHashMap<>();
        backups.put("keep", (long) backupKeep);
        backups.put("autosaveMinutes", (long) autosaveMinutes);
        m.put("backups", backups);
        m.put("recentProjects", (long) recentProjects);
        m.put("keybindings", keys.toJson());
        String text = Json.write(m);
        Path target = loadedFrom != null ? loadedFrom : primary;
        try {
            write(target, text);
        } catch (IOException e) {
            if (target.equals(fallback())) throw e;
            target = fallback();
            write(target, text);
        }
        loadedFrom = target;
        loadedModified = Files.getLastModifiedTime(target).toMillis();
        return target;
    }

    private static void write(Path f, String text) throws IOException {
        Files.createDirectories(f.toAbsolutePath().getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
    }
}
