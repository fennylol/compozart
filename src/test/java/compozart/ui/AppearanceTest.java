package compozart.ui;

import compozart.io.ProjectIO;
import compozart.model.Project;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static compozart.test.Check.*;

public class AppearanceTest {
    public void testBuiltInThemes() {
        List<String> problems = new ArrayList<>();
        Map<String, Appearance.Theme> t = Appearance.parseThemes(Appearance.defaultText(Appearance.THEMES), null, problems);
        eq(List.of(), problems);
        eq(List.of("Default dark", "Catppuccin Mocha", "Catppuccin Latte"), List.copyOf(t.keySet()));
        yes(t.get("Default dark").dark(), "dark base");
        no(t.get("Catppuccin Latte").dark(), "light base");
        eq(new Color(0xfab387), t.get("Default dark").colors().get("rootAnchor"));
        eq(new Color(0xcba6f7), t.get("Catppuccin Mocha").icons().get('v'));
        eq("#1e1e2e", t.get("Catppuccin Mocha").flatlaf().get("@background"));
    }

    public void testUserThemeFallsBackForMissingColors() {
        List<String> problems = new ArrayList<>();
        Appearance.Theme base = Appearance.parseThemes(Appearance.defaultText(Appearance.THEMES), null, problems)
                .get("Default dark");
        Map<String, Appearance.Theme> t = Appearance.parseThemes("""
                {"themes": {"Mine": {"base": "light", "colors": {"accent": "#112233", "grid": "nope"},
                 "icons": {"v": "#010203", "toolong": "#000000"}}}}""", base, problems);
        Appearance.Theme mine = t.get("Mine");
        eq(new Color(0x112233), mine.colors().get("accent"));
        eq(base.colors().get("rootAnchor"), mine.colors().get("rootAnchor"));
        eq(new Color(0x010203), mine.icons().get('v'));
        eq(base.icons().get('y'), mine.icons().get('y'));
        eq(2, problems.size()); // the bad color and the bad icon key
    }

    public void testBuiltInIconsAndBadGrids() {
        List<String> problems = new ArrayList<>();
        Map<String, String[]> icons = Appearance.parseIcons(Appearance.defaultText(Appearance.ICONS), problems);
        eq(List.of(), problems);
        eq(16, icons.size());
        Map<String, String[]> user = Appearance.parseIcons("{\"icons\": {\"draw\": [\"#\"], \"new\": "
                + "[" + "\"################\",".repeat(15) + "\"################\"]}}", problems);
        eq(1, user.size());
        yes(user.containsKey("new"), "the good grid loads");
        eq(1, problems.size());
    }

    public void testBackupsKeepTheNewest() throws Exception {
        Path dir = Files.createTempDirectory("backups");
        Backups b = new Backups(dir);
        Project p = Project.createDefault();
        LocalDateTime t = LocalDateTime.of(2026, 10, 6, 12, 0, 0);
        for (int i = 0; i < 5; i++) b.backup(p, "my/creature", 3, t.plusSeconds(i));
        b.backup(p, "my/creature", 3, t.plusSeconds(4)); // same second: gets a suffix
        b.autosave(p, "my/creature");
        Path folder = dir.resolve("my_creature");
        List<String> names;
        try (Stream<Path> s = Files.list(folder)) {
            names = s.map(f -> f.getFileName().toString()).sorted().toList();
        }
        eq(List.of("my_creature 2026-10-06 120003.zart", "my_creature 2026-10-06 120004-2.zart",
                "my_creature 2026-10-06 120004.zart", "my_creature autosave.zart"), names);
        ProjectIO.load(folder.resolve("my_creature autosave.zart"));
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(f);
        }
    }

    public void testSettingsBackupOptions() throws Exception {
        Path dir = Files.createTempDirectory("settings");
        Path f = dir.resolve(Settings.FILE_NAME);
        Files.writeString(f, "{\"version\": 4, \"backups\": {\"keep\": 7, \"autosaveMinutes\": 0}, \"keybindings\": {}}");
        Settings s = new Settings(f);
        eq(null, s.load(new KeyMap()));
        eq(7, s.backupKeep);
        eq(0, s.autosaveMinutes);
        s.save(new KeyMap());
        yes(Files.readString(f).contains("\"backups\": {\"keep\": 7, \"autosaveMinutes\": 0}"), "written back");
        Files.delete(f);
        Files.delete(dir);
    }
}
