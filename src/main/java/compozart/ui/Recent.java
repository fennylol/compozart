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

/** Recently opened or saved projects, newest first, kept in {@code settings/recent.json}. */
final class Recent {
    static final String FILE_NAME = "recent.json";

    private final Path file;

    Recent() {
        this(locate());
    }

    Recent(Path file) {
        this.file = file;
    }

    private static Path locate() {
        Path primary = AppHome.settingsDir().resolve(FILE_NAME);
        return Files.exists(primary) || AppHome.writable(primary.getParent()) ? primary : Config.dir().resolve(FILE_NAME);
    }

    /** Every remembered path, newest first, including ones that no longer exist. */
    List<Path> all() {
        List<Path> out = new ArrayList<>();
        try {
            if (!Files.exists(file)) return out;
            Map<String, Object> m = Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8)), FILE_NAME);
            for (Object o : Json.arr(m.get("projects"), "projects")) {
                Path p = Path.of(String.valueOf(o)).toAbsolutePath().normalize();
                if (!out.contains(p)) out.add(p);
            }
        } catch (IOException | RuntimeException e) {
            // a broken file only loses the list
        }
        return out;
    }

    /** The newest {@code max} projects that still exist. */
    List<Path> existing(int max) {
        List<Path> out = new ArrayList<>();
        for (Path p : all()) {
            if (out.size() >= max) break;
            if (Files.isRegularFile(p)) out.add(p);
        }
        return out;
    }

    /** Moves {@code p} to the front and keeps at most {@code max} entries. */
    void add(Path p, int max) {
        Path abs = p.toAbsolutePath().normalize();
        List<Path> list = all();
        list.remove(abs);
        list.add(0, abs);
        while (list.size() > Math.max(1, max)) list.remove(list.size() - 1);
        write(list);
    }

    void remove(Path p) {
        List<Path> list = all();
        if (list.remove(p.toAbsolutePath().normalize())) write(list);
    }

    private void write(List<Path> list) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> paths = new ArrayList<>();
        for (Path p : list) paths.add(p.toString());
        m.put("projects", paths);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Json.write(m), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // the list is a convenience; failing to store it is not worth interrupting anyone
        }
    }
}
