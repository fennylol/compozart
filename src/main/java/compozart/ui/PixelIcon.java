package compozart.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Small pixel-art icons for tool and library buttons, drawn from 16x16 text grids so they can be retouched by hand.
 * {@code #} is the theme's text color and {@code m} a muted version of it; the other letters are Catppuccin
 * accents. Disabled buttons draw every pixel in the disabled text color.
 */
final class PixelIcon implements Icon {
    static final int SIZE = 16;

    private static final Map<Character, Color> ACCENTS = Map.of(
            'p', new Color(0xf5c2e7), 'y', new Color(0xf9e2af), 'o', new Color(0xfab387), 'b', new Color(0x89b4fa),
            'g', new Color(0xa6e3a1), 's', new Color(0x89dceb), 'v', new Color(0xcba6f7), 'r', new Color(0xf38ba8),
            'w', new Color(0xbac2de));

    private static final Map<String, String[]> GRIDS = Map.ofEntries(
            Map.entry("draw", new String[]{
                    "................",
                    "................",
                    "............#p..",
                    "...........#pp#.",
                    "..........#mp#..",
                    ".........#ym#...",
                    "........#yy#....",
                    ".......#yy#.....",
                    "......#yy#......",
                    ".....#yy#.......",
                    "....oyy#........",
                    "...ooo#.........",
                    "..##oo..........",
                    "...##...........",
                    "................",
                    "................"}),
            Map.entry("eraser", new String[]{
                    "................",
                    "................",
                    "................",
                    "................",
                    "........##......",
                    ".......#pp#.....",
                    "......#pppp#....",
                    ".....#pppppp#...",
                    "....#wwpppp#....",
                    "...#wwwwpp#.....",
                    "...#wwwww#......",
                    "....#www#.......",
                    ".....#w#........",
                    "......#.........",
                    "........mmmmmmm.",
                    "................"}),
            Map.entry("fill", new String[]{
                    "................",
                    ".....####.......",
                    "....#....#......",
                    "...#......#.....",
                    "..##########....",
                    "..#bbbbbbbb#b...",
                    "..#bbbbbbbb#bb..",
                    "..#mmmmmmmm#.bb.",
                    "...#mmmmmm#...b.",
                    "...#mmmmmm#..bbb",
                    "...#mmmmmm#..bbb",
                    "....#mmmm#....b.",
                    "....######......",
                    "................",
                    "................",
                    "................"}),
            Map.entry("select", new String[]{
                    "................",
                    "................",
                    "................",
                    "..#.##..##..##..",
                    "................",
                    "................",
                    "..#..........#..",
                    "..#..........#..",
                    "................",
                    "................",
                    "..#..........#..",
                    "..#..........#..",
                    "....##..##..#...",
                    "................",
                    "................",
                    "................"}),
            Map.entry("eyedropper", new String[]{
                    "................",
                    "...........##...",
                    "..........####..",
                    "..........####..",
                    "........##.##...",
                    ".........##.....",
                    "........###.....",
                    ".......#w#......",
                    "......#w#.......",
                    ".....#v#........",
                    "....#v#.........",
                    "...#v#..........",
                    "...v#...........",
                    "..#.............",
                    ".#..............",
                    "....v..........."}),
            Map.entry("line", new String[]{
                    "................",
                    "............vvv.",
                    "............vvv.",
                    "............vvv.",
                    "...........##...",
                    "..........##....",
                    ".........##.....",
                    "........##......",
                    ".......##.......",
                    "......##........",
                    ".....##.........",
                    "....##..........",
                    ".vvv#...........",
                    ".vvv............",
                    ".vvv............",
                    "................"}),
            Map.entry("rect", new String[]{
                    "................",
                    "................",
                    ".vvv........vvv.",
                    ".vvv########vvv.",
                    ".vvv........vvv.",
                    "..#..........#..",
                    "..#..........#..",
                    "..#..........#..",
                    "..#..........#..",
                    "..#..........#..",
                    "..#..........#..",
                    ".vvv........vvv.",
                    ".vvv########vvv.",
                    ".vvv........vvv.",
                    "................",
                    "................"}),
            Map.entry("anchor", new String[]{
                    "................",
                    ".......ss.......",
                    "......ssss......",
                    ".....ssssss.....",
                    "....ssssssss....",
                    ".......ss.......",
                    ".......ss.......",
                    ".......ss.......",
                    ".......ss.......",
                    ".......ss.......",
                    ".......ss.......",
                    "......oooo......",
                    "......oooo......",
                    "......oooo......",
                    "......oooo......",
                    "................"}),
            Map.entry("new", new String[]{
                    "................",
                    "................",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    "..gggggggggggg..",
                    "..gggggggggggg..",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    ".......gg.......",
                    "................",
                    "................"}),
            Map.entry("rename", new String[]{
                    "................",
                    "................",
                    "..........###...",
                    "...........#....",
                    ".mmmmmmmmmm#mmm.",
                    ".m.........#..m.",
                    ".m.........#..m.",
                    ".m.######..#..m.",
                    ".m.######..#..m.",
                    ".m.........#..m.",
                    ".m.........#..m.",
                    ".mmmmmmmmmm#mmm.",
                    "...........#....",
                    "..........###...",
                    "................",
                    "................"}),
            Map.entry("duplicate", new String[]{
                    "................",
                    ".mmmmmmmmm......",
                    ".m.......m......",
                    ".m.......m......",
                    ".m.......m......",
                    ".m...##########.",
                    ".m...#........#.",
                    ".m...#........#.",
                    ".m...#.vvvvvv.#.",
                    ".mmmm#........#.",
                    ".....#.vvvvvv.#.",
                    ".....#........#.",
                    ".....#.vvvv...#.",
                    ".....#........#.",
                    ".....##########.",
                    "................"}),
            Map.entry("resize", new String[]{
                    "................",
                    "..........#####.",
                    ".............##.",
                    "............#.#.",
                    "...........#..#.",
                    "..........#...#.",
                    ".mmmmmmmm#......",
                    ".m......#m......",
                    ".m.....#.m......",
                    ".m.......m......",
                    ".m.......m......",
                    ".m.......m......",
                    ".m.......m......",
                    ".m.......m......",
                    ".mmmmmmmmm......",
                    "................"}),
            Map.entry("root", new String[]{
                    "................",
                    "................",
                    "................",
                    "..y....yy....y..",
                    "..yy..yyyy..yy..",
                    "..yyy.yyyy.yyy..",
                    "..yyyyyyyyyyyy..",
                    "..yyyyyyyyyyyy..",
                    "..yyrryyyybbyy..",
                    "..yyrryyyybbyy..",
                    "..yyyyyyyyyyyy..",
                    "..oooooooooooo..",
                    "..yyyyyyyyyyyy..",
                    "................",
                    "................",
                    "................"}),
            Map.entry("up", new String[]{
                    "................",
                    "................",
                    "................",
                    "................",
                    ".......##.......",
                    "......####......",
                    ".....######.....",
                    "....########....",
                    "...##########...",
                    "..############..",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"}),
            Map.entry("down", new String[]{
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "..############..",
                    "...##########...",
                    "....########....",
                    ".....######.....",
                    "......####......",
                    ".......##.......",
                    "................",
                    "................",
                    "................",
                    "................"}),
            Map.entry("delete", new String[]{
                    "................",
                    "......####......",
                    "......#..#......",
                    "..rrrrrrrrrrrr..",
                    "................",
                    "...##########...",
                    "...#........#...",
                    "...#.m.mm.m.#...",
                    "...#.m.mm.m.#...",
                    "...#.m.mm.m.#...",
                    "...#.m.mm.m.#...",
                    "...#.m.mm.m.#...",
                    "...#.m.mm.m.#...",
                    "...#........#...",
                    "...##########...",
                    "................"}));

    private final String[] grid;

    private PixelIcon(String[] grid) {
        this.grid = grid;
    }

    static PixelIcon of(String name) {
        String[] g = GRIDS.get(name);
        if (g == null) throw new IllegalArgumentException("no icon " + name);
        return new PixelIcon(g);
    }

    static boolean exists(String name) {
        return GRIDS.containsKey(name);
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
        for (int row = 0; row < SIZE; row++) {
            String r = grid[row];
            for (int col = 0; col < SIZE; col++) {
                char ch = r.charAt(col);
                if (ch == '.') continue;
                Color color = !enabled ? disabled : ch == '#' ? fg : ch == 'm' ? muted : ACCENTS.getOrDefault(ch, fg);
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

    /** Every grid must be 16 rows of 16 known characters. Used by the tests. */
    static void validate() {
        for (var e : GRIDS.entrySet()) {
            String[] g = e.getValue();
            if (g.length != SIZE) throw new IllegalStateException(e.getKey() + " has " + g.length + " rows");
            for (String r : g) {
                if (r.length() != SIZE) throw new IllegalStateException(e.getKey() + " has a row of " + r.length());
                for (char ch : r.toCharArray()) {
                    if (ch != '.' && ch != '#' && ch != 'm' && !ACCENTS.containsKey(ch)) {
                        throw new IllegalStateException(e.getKey() + " uses unknown color " + ch);
                    }
                }
            }
        }
    }
}
