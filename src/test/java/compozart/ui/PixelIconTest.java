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
}
