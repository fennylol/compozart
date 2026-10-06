package compozart.ui;

import compozart.io.ProjectIO;
import compozart.model.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies of projects in the backups folder, one subfolder per project name.
 * Every save adds a timestamped copy; unsaved work is autosaved to a single file that each autosave replaces.
 */
final class Backups {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");
    static final String AUTOSAVE = "autosave";

    private final Path dir;

    Backups(Path dir) {
        this.dir = dir;
    }

    Path dir() {
        return dir;
    }

    /** A project name made safe for file names on every platform. */
    static String safe(String base) {
        String s = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "untitled" : s;
    }

    private Path folder(String base) throws IOException {
        Path f = dir.resolve(safe(base));
        Files.createDirectories(f);
        return f;
    }

    /** Writes a timestamped copy and removes the oldest ones beyond {@code keep}. Returns the new file. */
    Path backup(Project p, String base, int keep, LocalDateTime now) throws IOException {
        Path folder = folder(base);
        String name = safe(base) + " " + STAMP.format(now);
        Path f = folder.resolve(name + ProjectIO.EXTENSION);
        for (int i = 2; Files.exists(f); i++) f = folder.resolve(name + "-" + i + ProjectIO.EXTENSION);
        ProjectIO.save(p, f);
        prune(folder, safe(base), keep);
        return f;
    }

    /** Replaces the project's autosave file. */
    Path autosave(Project p, String base) throws IOException {
        Path f = folder(base).resolve(safe(base) + " " + AUTOSAVE + ProjectIO.EXTENSION);
        ProjectIO.save(p, f);
        return f;
    }

    /** Keeps the newest {@code keep} timestamped backups. The autosave file is never removed here. */
    static void prune(Path folder, String base, int keep) throws IOException {
        List<Path> stamped;
        try (Stream<Path> s = Files.list(folder)) {
            stamped = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(base + " ") && n.endsWith(ProjectIO.EXTENSION)
                        && !n.equals(base + " " + AUTOSAVE + ProjectIO.EXTENSION);
            }).sorted().toList();
        }
        for (int i = 0; i < stamped.size() - keep; i++) Files.deleteIfExists(stamped.get(i));
    }
}
