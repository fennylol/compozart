package critter.io;

import critter.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Reads and writes {@code .critter.json} save files. See SPEC.md, "Save file". */
public final class ProjectIO {
    public static final String EXTENSION = ".critter.json";
    private static final String FORMAT = "critter_inator";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProjectIO() {
    }

    public static void save(Project p, Path file) throws IOException {
        // Write to a temp file first so a failed save cannot destroy the previous one.
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, toJson(p), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Project load(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static String toJson(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("version", (long) Project.FORMAT_VERSION);
        m.put("seed", p.seed);
        m.put("root", p.root == null ? null : ref(p.root));
        List<Object> pal = new ArrayList<>();
        for (int i = 0; i < p.palette.size(); i++) {
            Palette.Swatch s = p.palette.get(i);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", s.name());
            e.put("color", hexColor(s.argb()));
            pal.add(e);
        }
        m.put("palette", pal);
        List<Object> nodes = new ArrayList<>();
        for (Node n : p.nodes) nodes.add(node(n));
        m.put("nodes", nodes);
        return Json.write(m);
    }

    private static Map<String, Object> ref(NodeRef r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("variant", r.variant());
        return m;
    }

    private static Map<String, Object> node(Node n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", n.name);
        m.put("variant", n.variant);
        m.put("size", (long) n.size());
        if (n.root == null) m.put("root", null);
        else {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("x", (long) n.root.x());
            r.put("y", (long) n.root.y());
            r.put("dir", n.root.dir().name());
            m.put("root", r);
        }
        List<Object> anchors = new ArrayList<>();
        for (NamedAnchor a : n.anchors) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("target", a.target);
            am.put("x", (long) a.x);
            am.put("y", (long) a.y);
            am.put("dir", a.dir.name());
            am.put("variant", a.variant);
            am.put("group", a.group);
            am.put("layer", (long) a.layerModifier);
            am.put("mirrored", a.mirrored);
            am.put("depth", a.depth == null ? null : (long) a.depth);
            am.put("endVariant", a.endVariant);
            anchors.add(am);
        }
        m.put("anchors", anchors);
        List<Object> rows = new ArrayList<>();
        int s = n.size();
        byte[] px = n.pixels();
        for (int y = 0; y < s; y++) {
            char[] row = new char[s * 2];
            for (int x = 0; x < s; x++) {
                int v = px[y * s + x] & 0xff;
                row[x * 2] = HEX[v >> 4];
                row[x * 2 + 1] = HEX[v & 15];
            }
            rows.add(new String(row));
        }
        m.put("pixels", rows);
        return m;
    }

    public static Project fromJson(String text) {
        Map<String, Object> m = Json.obj(Json.parse(text), "file");
        if (!FORMAT.equals(m.get("format"))) throw new IllegalArgumentException("not a critter_inator file");
        long version = Json.num(m, "version", 0);
        if (version < 1 || version > Project.FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported file version " + version);
        }

        Palette pal = new Palette();
        List<Object> entries = Json.arr(m.get("palette"), "palette");
        if (entries.isEmpty()) throw new IllegalArgumentException("palette is empty");
        if (entries.size() > Palette.MAX) throw new IllegalArgumentException("palette has more than 256 entries");
        for (int i = 1; i < entries.size(); i++) {
            Map<String, Object> e = Json.obj(entries.get(i), "palette entry");
            pal.add(new Palette.Swatch(parseColor(Json.str(e, "color", "#000000ff")), Json.str(e, "name", "")));
        }

        Project p = new Project(pal);
        p.seed = Json.num(m, "seed", 0);
        Object root = m.get("root");
        if (root != null) {
            Map<String, Object> r = Json.obj(root, "root");
            p.root = new NodeRef(Json.str(r, "name", ""), Json.str(r, "variant", ""));
        }
        for (Object o : Json.arr(m.get("nodes"), "nodes")) p.nodes.add(readNode(Json.obj(o, "node"), pal.size()));
        Set<NodeRef> seen = new HashSet<>();
        for (Node n : p.nodes) {
            if (!seen.add(n.ref())) throw new IllegalArgumentException("duplicate node " + n.ref());
        }
        return p;
    }

    private static Node readNode(Map<String, Object> m, int paletteSize) {
        String name = Json.str(m, "name", "");
        int size = (int) Json.num(m, "size", Node.DEFAULT_SIZE);
        if (size < 1 || size > Node.MAX_SIZE) throw new IllegalArgumentException("node " + name + " has bad size " + size);
        Node n = new Node(name, Json.str(m, "variant", ""), size);
        Object root = m.get("root");
        if (root != null) {
            Map<String, Object> r = Json.obj(root, "root anchor");
            n.root = new RootAnchor(coord(r, "x", size), coord(r, "y", size), Dir.valueOf(Json.str(r, "dir", "N")));
        }
        Object anchors = m.get("anchors");
        if (anchors != null) {
            for (Object o : Json.arr(anchors, "anchors")) {
                Map<String, Object> am = Json.obj(o, "anchor");
                NamedAnchor a = new NamedAnchor(Json.str(am, "target", ""), coord(am, "x", size), coord(am, "y", size),
                        Dir.valueOf(Json.str(am, "dir", "N")));
                a.variant = Json.str(am, "variant", null);
                a.group = Json.str(am, "group", "");
                a.layerModifier = (int) Json.num(am, "layer", 1);
                a.mirrored = Json.bool(am, "mirrored", false);
                Object depth = am.get("depth");
                a.depth = depth == null ? null : (int) Json.num(am, "depth", 1);
                a.endVariant = Json.str(am, "endVariant", null);
                n.anchors.add(a);
            }
        }
        List<Object> rows = Json.arr(m.get("pixels"), "pixels of " + name);
        if (rows.size() != size) throw new IllegalArgumentException("node " + name + " has " + rows.size() + " pixel rows, expected " + size);
        for (int y = 0; y < size; y++) {
            if (!(rows.get(y) instanceof String row) || row.length() != size * 2) {
                throw new IllegalArgumentException("node " + name + " pixel row " + y + " has the wrong length");
            }
            for (int x = 0; x < size; x++) {
                int v = Integer.parseInt(row.substring(x * 2, x * 2 + 2), 16);
                if (v >= paletteSize) throw new IllegalArgumentException("node " + name + " uses palette index " + v + " beyond the palette");
                n.set(x, y, v);
            }
        }
        return n;
    }

    private static int coord(Map<String, Object> m, String key, int size) {
        long v = Json.num(m, key, 0);
        if (v < 0 || v >= size) throw new IllegalArgumentException("anchor " + key + " out of range: " + v);
        return (int) v;
    }

    /** {@code #rrggbbaa}, lowercase. */
    public static String hexColor(int argb) {
        return String.format("#%06x%02x", argb & 0xffffff, argb >>> 24);
    }

    /** Parses {@code #rrggbb} or {@code #rrggbbaa} into ARGB. */
    public static int parseColor(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        if (!h.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("bad color " + s);
        int rgb = Integer.parseInt(h.substring(0, 6), 16);
        int a = h.length() == 8 ? Integer.parseInt(h.substring(6), 16) : 255;
        return (a << 24) | rgb;
    }
}
