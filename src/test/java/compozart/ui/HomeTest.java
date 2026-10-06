package compozart.ui;

import compozart.io.ProjectIO;
import compozart.model.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static compozart.test.Check.*;

public class HomeTest {
    static Path temp() throws Exception {
        return Files.createTempDirectory("home");
    }

    static void delete(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(f);
        }
    }

    public void testRecentMovesToFrontAndTrims() throws Exception {
        Path dir = temp();
        Recent r = new Recent(dir.resolve("recent.json"));
        Path a = dir.resolve("a.zart"), b = dir.resolve("b.zart"), c = dir.resolve("c.zart");
        for (Path p : List.of(a, b, c)) Files.writeString(p, "x");
        r.add(a, 2);
        r.add(b, 2);
        r.add(a, 2);
        eq(List.of(a, b), r.all());
        r.add(c, 2);
        eq(List.of(c, a), r.all());
        Files.delete(a);
        eq(List.of(c), r.existing(10)); // missing files are skipped
        r.remove(c);
        eq(List.of(a), r.all());
        delete(dir);
    }

    public void testProjectsInSkipsBackupsAndSortsNewestFirst() throws Exception {
        Path dir = temp();
        Project p = Project.createDefault();
        Path old = dir.resolve("old.zart"), fresh = dir.resolve("sub/fresh.zart"), backup = dir.resolve("backups/x/x 1.zart");
        Files.createDirectories(fresh.getParent());
        Files.createDirectories(backup.getParent());
        for (Path f : List.of(old, fresh, backup)) ProjectIO.save(p, f);
        Files.writeString(dir.resolve("notes.txt"), "not a project");
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(fresh, FileTime.fromMillis(2_000_000));
        eq(List.of(fresh.toAbsolutePath().normalize(), old.toAbsolutePath().normalize()), HomeWindow.projectsIn(dir));
        eq(List.of(), HomeWindow.projectsIn(dir.resolve("missing")));
        delete(dir);
    }

    public void testRecentProjectsSetting() throws Exception {
        Path dir = temp();
        Path f = dir.resolve(Settings.FILE_NAME);
        Files.writeString(f, "{\"version\": 4, \"recentProjects\": 3}");
        Settings s = new Settings(f);
        eq(null, s.load(null));
        eq(3, s.recentProjects);
        eq("{\"version\": 4, \"recentProjects\": 3}", Files.readString(f)); // loading without keys never writes
        delete(dir);
    }

    public void testShortenKeepsTheEnd() {
        eq("~/games/creatures", HomeWindow.shorten("~/games/creatures", 60));
        String s = HomeWindow.shorten("/tmp/a-very-long-folder-name/another-long-one/and/then/creatures/drafts", 30);
        yes(s.startsWith("\u2026/") && s.endsWith("creatures/drafts"), s);
        yes(s.length() <= 30, s);
    }
}
