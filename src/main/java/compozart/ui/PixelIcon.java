package compozart.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Small pixel-art icons for tool and library buttons, drawn from the 16x16 text grids in icons.json.
 * {@code #} is the theme's text color and {@code m} a muted version of it; the other letters take their colors
 * from the theme's "icons" table. Disabled buttons draw every pixel in the disabled text color.
 */
final class PixelIcon implements Icon {
    static final int SIZE = 16;

    /** The icons shipped in the jar, used when icons.json leaves one out or before it is loaded. */
    private static Map<String, String[]> builtIn;

    private static synchronized Map<String, String[]> builtIn() {
        if (builtIn == null) builtIn = Appearance.parseIcons(Appearance.defaultText(Appearance.ICONS), new java.util.ArrayList<>());
        return builtIn;
    }

    static String[] grid(String name) {
        String[] g = Appearance.get().icon(name);
        return g != null ? g : builtIn().get(name);
    }

    private final String name;

    private PixelIcon(String name) {
        this.name = name;
    }

    /** An icon that always draws the current grid and theme colors for {@code name}, so reloads show at once. */
    static PixelIcon of(String name) {
        if (!exists(name)) throw new IllegalArgumentException("no icon " + name);
        return new PixelIcon(name);
    }

    static boolean exists(String name) {
        return grid(name) != null;
    }

    /** Whole pixels per grid cell, following FlatLaf's UI scale so icons stay crisp on high-DPI screens. */
    private static int scale() {
        return Math.max(1, Math.round(com.formdev.flatlaf.util.UIScale.getUserScaleFactor()));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        int s = scale();
        boolean enabled = c == null || c.isEnabled();
        Color fg = UIManager.getColor("Label.foreground");
        Color disabled = UIManager.getColor("Label.disabledForeground");
        if (fg == null) fg = Color.LIGHT_GRAY;
        if (disabled == null) disabled = Color.GRAY;
        Color muted = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120);
        String[] grid = grid(name);
        Appearance.Theme theme = Appearance.get().themeOrNull();
        Map<Character, Color> accents = theme == null ? DEFAULT_ACCENTS : theme.icons();
        for (int row = 0; row < SIZE; row++) {
            String r = grid[row];
            for (int col = 0; col < SIZE; col++) {
                char ch = r.charAt(col);
                if (ch == '.') continue;
                Color color = !enabled ? disabled : ch == '#' ? fg : ch == 'm' ? muted : accents.getOrDefault(ch, fg);
                g.setColor(color);
                g.fillRect(x + col * s, y + row * s, s, s);
            }
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE * scale();
    }

    @Override
    public int getIconHeight() {
        return SIZE * scale();
    }

    /** Colors for icon letters before a theme is loaded. */
    private static final Map<Character, Color> DEFAULT_ACCENTS = Map.of(
            'p', new Color(0xf5c2e7), 'y', new Color(0xf9e2af), 'o', new Color(0xfab387), 'b', new Color(0x89b4fa),
            'g', new Color(0xa6e3a1), 's', new Color(0x89dceb), 'v', new Color(0xcba6f7), 'r', new Color(0xf38ba8),
            'w', new Color(0xbac2de), 'k', new Color(0x11111b));

    /** Every built-in grid must be 16 rows of 16 known characters. Used by the tests. */
    static void validate() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        Map<String, String[]> grids = Appearance.parseIcons(Appearance.defaultText(Appearance.ICONS), problems);
        if (!problems.isEmpty()) throw new IllegalStateException(String.join("; ", problems));
        for (var e : grids.entrySet()) {
            for (String r : e.getValue()) {
                for (char ch : r.toCharArray()) {
                    if (ch != '.' && ch != '#' && ch != 'm' && !DEFAULT_ACCENTS.containsKey(ch)) {
                        throw new IllegalStateException(e.getKey() + " uses unknown color " + ch);
                    }
                }
            }
        }
    }
}
