package critter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared indexed palette. Index 0 is always fully clear and cannot be edited, moved or deleted.
 * Colors are non-premultiplied ARGB ints.
 */
public final class Palette {
    public static final int MAX = 256;

    public record Swatch(int argb, String name) {
        public Swatch {
            name = name == null ? "" : name;
        }

        public int alpha() {
            return argb >>> 24;
        }
    }

    private final List<Swatch> entries = new ArrayList<>();

    public Palette() {
        entries.add(new Swatch(0, "clear"));
    }

    public int size() {
        return entries.size();
    }

    public Swatch get(int i) {
        return entries.get(i);
    }

    public int argb(int i) {
        return entries.get(i).argb;
    }

    public void set(int i, Swatch s) {
        requireEditable(i);
        entries.set(i, s);
    }

    /** Appends an entry and returns its index. */
    public int add(Swatch s) {
        if (entries.size() >= MAX) throw new IllegalStateException("palette is full (" + MAX + " entries)");
        entries.add(s);
        return entries.size() - 1;
    }

    public boolean full() {
        return entries.size() >= MAX;
    }

    public Palette copy() {
        Palette p = new Palette();
        p.entries.clear();
        p.entries.addAll(entries);
        return p;
    }

    /**
     * Moves entry {@code from} to position {@code to}.
     *
     * @return the index map to apply to pixels, old index to new index
     */
    public int[] move(int from, int to) {
        requireEditable(from);
        requireEditable(to);
        int[] order = new int[entries.size()];
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) idx.add(i);
        idx.add(to, idx.remove(from));
        List<Swatch> next = new ArrayList<>();
        for (int newPos = 0; newPos < idx.size(); newPos++) {
            order[idx.get(newPos)] = newPos;
            next.add(entries.get(idx.get(newPos)));
        }
        entries.clear();
        entries.addAll(next);
        return order;
    }

    /**
     * Deletes entry {@code i}. Pixels that used it move to {@code replacement}.
     *
     * @param replacement an index in the palette before the deletion, not {@code i}
     * @return the index map to apply to pixels, old index to new index
     */
    public int[] remove(int i, int replacement) {
        requireEditable(i);
        if (replacement == i || replacement < 0 || replacement >= entries.size()) {
            throw new IllegalArgumentException("bad replacement index " + replacement);
        }
        int[] map = new int[entries.size()];
        for (int k = 0; k < map.length; k++) map[k] = k < i ? k : k - 1;
        map[i] = map[replacement];
        entries.remove(i);
        return map;
    }

    private void requireEditable(int i) {
        if (i <= 0 || i >= entries.size()) throw new IllegalArgumentException("palette index " + i + " is not editable");
    }

    /** Index 0 plus the 26 Catppuccin Mocha colors, the default for new projects. */
    public static Palette catppuccinMocha() {
        Palette p = new Palette();
        String[][] mocha = {
                {"rosewater", "f5e0dc"}, {"flamingo", "f2cdcd"}, {"pink", "f5c2e7"}, {"mauve", "cba6f7"},
                {"red", "f38ba8"}, {"maroon", "eba0ac"}, {"peach", "fab387"}, {"yellow", "f9e2af"},
                {"green", "a6e3a1"}, {"teal", "94e2d5"}, {"sky", "89dceb"}, {"sapphire", "74c7ec"},
                {"blue", "89b4fa"}, {"lavender", "b4befe"}, {"text", "cdd6f4"}, {"subtext1", "bac2de"},
                {"subtext0", "a6adc8"}, {"overlay2", "9399b2"}, {"overlay1", "7f849c"}, {"overlay0", "6c7086"},
                {"surface2", "585b70"}, {"surface1", "45475a"}, {"surface0", "313244"}, {"base", "1e1e2e"},
                {"mantle", "181825"}, {"crust", "11111b"},
        };
        for (String[] c : mocha) p.add(new Swatch(0xff000000 | Integer.parseInt(c[1], 16), c[0]));
        return p;
    }
}
