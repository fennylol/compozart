package compozart.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import compozart.io.Json;
import compozart.io.ProjectIO;

import javax.swing.*;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Themes and icons, read from {@code themes.json} and {@code icons.json} in the settings folder.
 * Missing files are written from the defaults inside the jar, so there is always something to edit.
 * Anything a user file leaves out falls back to those defaults.
 */
public final class Appearance {
    static final String THEMES = "themes.json", ICONS = "icons.json";

    /** One theme: a FlatLaf base, FlatLaf keys, the app's own colors, and the colors for icon letters. */
    record Theme(String name, boolean dark, Map<String, String> flatlaf, Map<String, Color> colors,
                 Map<Character, Color> icons) {
    }

    private static final Appearance INSTANCE = new Appearance();

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private String active;
    private Map<String, String[]> icons = new HashMap<>();
    private final Map<String, Long> modified = new HashMap<>();
    private final Map<String, Path> paths = new HashMap<>();

    private Appearance() {
    }

    public static Appearance get() {
        return INSTANCE;
    }

    /** Loads both files and applies the active theme. Call before any window is created. */
    public String loadAndApply() {
        List<String> problems = new ArrayList<>();
        loadThemes(problems);
        loadIcons(problems);
        apply();
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    // ---- files ----

    static String defaultText(String name) {
        try (InputStream in = Appearance.class.getResourceAsStream("/compozart/defaults/" + name)) {
            if (in == null) throw new IllegalStateException("missing built-in " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Where a settings file lives: the app's settings folder, or the per-user folder when that is read-only. */
    private Path path(String name) {
        return paths.computeIfAbsent(name, n -> {
            Path primary = AppHome.settingsDir().resolve(n);
            if (Files.exists(primary) || AppHome.writable(primary.getParent())) return primary;
            return Config.dir().resolve(n);
        });
    }

    /** The file's text, writing the built-in default first when it does not exist. */
    private String read(String name, List<String> problems) {
        Path p = path(name);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, defaultText(name), StandardCharsets.UTF_8);
            }
            modified.put(name, Files.getLastModifiedTime(p).toMillis());
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add("could not use " + p + " (" + e.getMessage() + "); using built-in " + name);
            return defaultText(name);
        }
    }

    private boolean changed(String name) {
        Path p = path(name);
        try {
            return Files.exists(p) && Files.getLastModifiedTime(p).toMillis() != modified.getOrDefault(name, -1L);
        } catch (IOException e) {
            return false;
        }
    }

    /** Reloads whichever file changed on disk and reapplies. Returns a status message, or null. */
    String reloadIfChanged() {
        boolean t = changed(THEMES), i = changed(ICONS);
        if (!t && !i) return null;
        List<String> problems = new ArrayList<>();
        if (t) loadThemes(problems);
        if (i) loadIcons(problems);
        apply();
        return problems.isEmpty() ? "Reloaded " + (t && i ? THEMES + " and " + ICONS : t ? THEMES : ICONS)
                : String.join("; ", problems);
    }

    // ---- themes ----

    private void loadThemes(List<String> problems) {
        Map<String, Theme> builtIn = parseThemes(defaultText(THEMES), null, problems);
        Theme base = builtIn.values().iterator().next();
        themes.clear();
        try {
            Map<String, Object> file = Json.obj(Json.parse(read(THEMES, problems)), THEMES);
            themes.putAll(parseThemes(Json.write(file), base, problems));
            active = Json.str(file, "theme", null);
        } catch (RuntimeException e) {
            problems.add(THEMES + ": " + e.getMessage());
        }
        if (themes.isEmpty()) themes.putAll(builtIn);
        if (active == null || !themes.containsKey(active)) {
            if (active != null) problems.add(THEMES + ": no theme named “" + active + "”");
            active = themes.keySet().iterator().next();
        }
    }

    /** Parses the "themes" object. Colors a theme leaves out come from {@code fallback}. */
    static Map<String, Theme> parseThemes(String text, Theme fallback, List<String> problems) {
        Map<String, Object> root = Json.obj(Json.parse(text), THEMES);
        Map<String, Theme> out = new LinkedHashMap<>();
        for (var e : Json.obj(root.get("themes"), "themes").entrySet()) {
            Map<String, Object> t = Json.obj(e.getValue(), "theme " + e.getKey());
            boolean dark = !"light".equalsIgnoreCase(Json.str(t, "base", "dark"));
            Map<String, String> flatlaf = new LinkedHashMap<>();
            if (t.get("flatlaf") != null) {
                for (var f : Json.obj(t.get("flatlaf"), "flatlaf").entrySet()) flatlaf.put(f.getKey(), String.valueOf(f.getValue()));
            }
            Map<String, Color> colors = new LinkedHashMap<>(fallback == null ? Map.of() : fallback.colors());
            colors.putAll(parseColors(t.get("colors"), e.getKey(), problems));
            Map<Character, Color> icons = new LinkedHashMap<>(fallback == null ? Map.of() : fallback.icons());
            for (var c : parseColors(t.get("icons"), e.getKey(), problems).entrySet()) {
                if (c.getKey().length() == 1) icons.put(c.getKey().charAt(0), c.getValue());
                else problems.add("theme " + e.getKey() + ": icon color keys are single letters, not “" + c.getKey() + "”");
            }
            out.put(e.getKey(), new Theme(e.getKey(), dark, flatlaf, colors, icons));
        }
        return out;
    }

    private static Map<String, Color> parseColors(Object o, String theme, List<String> problems) {
        Map<String, Color> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (var e : Json.obj(o, "colors").entrySet()) {
            try {
                out.put(e.getKey(), new Color(ProjectIO.parseColor(String.valueOf(e.getValue())), true));
            } catch (IllegalArgumentException ex) {
                problems.add("theme " + theme + ": " + e.getKey() + " is not a color");
            }
        }
        return out;
    }

    List<String> themeNames() {
        return new ArrayList<>(themes.keySet());
    }

    String activeTheme() {
        return active;
    }

    Theme theme() {
        return themes.get(active);
    }

    /** The active theme, or null before anything is loaded (in tests). */
    Theme themeOrNull() {
        return active == null ? null : themes.get(active);
    }

    /** Switches theme, applies it, and records the choice in themes.json. */
    String select(String name) {
        if (!themes.containsKey(name) || name.equals(active)) return null;
        active = name;
        apply();
        Path p = path(THEMES);
        try {
            Map<String, Object> file = Json.obj(Json.parse(Files.readString(p, StandardCharsets.UTF_8)), THEMES);
            file.put("theme", name);
            Files.writeString(p, Json.write(file), StandardCharsets.UTF_8);
            modified.put(THEMES, Files.getLastModifiedTime(p).toMillis());
            return null;
        } catch (IOException | RuntimeException e) {
            return "Theme applied, but could not save the choice to " + p + ": " + e.getMessage();
        }
    }

    // ---- icons ----

    private void loadIcons(List<String> problems) {
        Map<String, String[]> next = parseIcons(defaultText(ICONS), problems);
        try {
            next.putAll(parseIcons(read(ICONS, problems), problems));
        } catch (RuntimeException e) {
            problems.add(ICONS + ": " + e.getMessage());
        }
        icons = next;
    }

    /** Reads the "icons" object, skipping grids that are not 16 rows of 16 characters. */
    static Map<String, String[]> parseIcons(String text, List<String> problems) {
        Map<String, Object> root = Json.obj(Json.parse(text), ICONS);
        Map<String, String[]> out = new HashMap<>();
        for (var e : Json.obj(root.get("icons"), "icons").entrySet()) {
            List<Object> rows = Json.arr(e.getValue(), "icon " + e.getKey());
            String[] grid = new String[rows.size()];
            boolean ok = rows.size() == PixelIcon.SIZE;
            for (int i = 0; i < rows.size() && ok; i++) {
                grid[i] = String.valueOf(rows.get(i));
                ok = grid[i].length() == PixelIcon.SIZE;
            }
            if (ok) out.put(e.getKey(), grid);
            else problems.add(ICONS + ": " + e.getKey() + " is not 16 rows of 16 characters; using the built-in one");
        }
        return out;
    }

    String[] icon(String name) {
        return icons.get(name);
    }

    // ---- applying ----

    /** Pushes the active theme into FlatLaf, the app's drawing colors, and the icon colors. */
    private void apply() {
        Theme t = theme();
        Draw.applyTheme(t.colors());
        boolean installed = UIManager.getLookAndFeel() instanceof FlatLaf;
        FlatLaf.setGlobalExtraDefaults(t.flatlaf());
        if (t.dark()) FlatDarkLaf.setup();
        else FlatLightLaf.setup();
        if (installed) FlatLaf.updateUI();
    }
}
