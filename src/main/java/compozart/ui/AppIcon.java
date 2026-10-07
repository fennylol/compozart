package compozart.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The window and taskbar icon: the "app" grid from icons.json, drawn in fixed colors so it looks the same in
 * every theme, at the sizes desktops ask for.
 */
public final class AppIcon {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final Map<Character, Color> COLORS = Map.ofEntries(
            Map.entry('#', new Color(0xcdd6f4)), Map.entry('m', new Color(0x9399b2)), Map.entry('k', new Color(0x11111b)),
            Map.entry('p', new Color(0xf5c2e7)), Map.entry('y', new Color(0xf9e2af)), Map.entry('o', new Color(0xfab387)),
            Map.entry('b', new Color(0x89b4fa)), Map.entry('g', new Color(0xa6e3a1)), Map.entry('s', new Color(0x74c7ec)),
            Map.entry('v', new Color(0xcba6f7)), Map.entry('r', new Color(0xf38ba8)), Map.entry('w', new Color(0xbac2de)));

    private AppIcon() {
    }

    /** The icon at every size, each scaled by whole pixels. */
    public static List<Image> images() {
        String[] grid = PixelIcon.grid("app");
        List<Image> out = new ArrayList<>();
        if (grid == null) return out;
        for (int size : SIZES) {
            int s = size / PixelIcon.SIZE;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            for (int y = 0; y < PixelIcon.SIZE; y++) {
                for (int x = 0; x < PixelIcon.SIZE; x++) {
                    char c = grid[y].charAt(x);
                    if (c == '.') continue;
                    g.setColor(COLORS.getOrDefault(c, Color.WHITE));
                    g.fillRect(x * s, y * s, s, s);
                }
            }
            g.dispose();
            out.add(img);
        }
        return out;
    }

    /** Sets the icon on a window, and on the macOS dock where that is supported. */
    public static void apply(Window w) {
        List<Image> images = images();
        if (images.isEmpty()) return;
        w.setIconImages(images);
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(images.get(images.size() - 1));
            }
        } catch (RuntimeException ignored) {
            // some desktops refuse; the window icon is enough
        }
    }
}
