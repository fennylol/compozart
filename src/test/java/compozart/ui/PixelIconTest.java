package compozart.ui;

import static compozart.test.Check.*;

public class PixelIconTest {
    public void testGridsAreWellFormed() {
        PixelIcon.validate();
    }

    public void testEveryToolHasAnIcon() {
        for (Tool t : Tool.values()) yes(PixelIcon.exists(t.icon), t + " has an icon");
    }

    public void testHelpMentionsBoundKeys() {
        String h = ToolHelp.html(Tool.DRAW, id -> id.equals("brush.smaller") ? "Shift+[" : id.equals("brush.larger") ? "Shift+]" : "", 200);
        yes(h.contains("Shift+["), "uses the bound key");
        eq("Draw (D)", ToolHelp.title(Tool.DRAW, id -> id.equals("tool.draw") ? "D" : ""));
        eq("Fill", ToolHelp.title(Tool.FILL, id -> ""));
        for (Tool t : Tool.values()) yes(ToolHelp.html(t, id -> "", 200).contains("&bull;"), t + " has help");
    }

    public void testAppIconSizes() {
        java.util.List<java.awt.Image> images = AppIcon.images();
        eq(java.util.List.of(16, 24, 32, 48, 64, 128, 256), images.stream().map(im -> im.getWidth(null)).toList());
        java.awt.image.BufferedImage big = (java.awt.image.BufferedImage) images.get(6);
        eq(0, big.getRGB(0, 0) >>> 24);                // transparent corner
        eq(0xff89b4fa, big.getRGB(8 * 16 + 8, 6 * 16)); // blue body, scaled by whole pixels
    }
}
