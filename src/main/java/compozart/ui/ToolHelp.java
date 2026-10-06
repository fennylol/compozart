package compozart.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** How to use each tool, written with the user's current key bindings. Shown in the panel under the library. */
final class ToolHelp {
    private ToolHelp() {
    }

    /**
     * @param keys  action id to a readable key, or "" when the action is unbound
     * @param width wrap width in pixels
     * @return HTML for a label
     */
    static String html(Tool tool, Function<String, String> keys, int width) {
        List<String> lines = new ArrayList<>();
        List<String> sizeKeys = new ArrayList<>();
        for (String id : List.of("brush.smaller", "brush.larger")) if (!keys.apply(id).isEmpty()) sizeKeys.add(keys.apply(id));
        String brush = (sizeKeys.isEmpty() ? "" : String.join(" / ", sizeKeys) + ", ") + "Ctrl+wheel or the Size box";
        switch (tool) {
            case DRAW -> {
                lines.add("Drag to paint with the selected color.");
                lines.add("Brush size: " + brush + ".");
                lines.add("Right-drag adds an anchor aimed at the cursor. Shift+right-drag places the root anchor.");
                lines.add("Right-click a pixel with an anchor to open its menu.");
                lines.add("Alt+click picks a color.");
            }
            case ERASER -> {
                lines.add("Drag to erase pixels.");
                lines.add("Right-click or right-drag erases anchors.");
                lines.add("Brush size: " + brush + ".");
            }
            case FILL -> {
                lines.add("Click to fill the connected area of one color, up, down, left and right.");
                lines.add("Right-click fills with clear.");
                lines.add("Alt+click picks a color.");
            }
            case SELECT -> {
                lines.add("Drag to select a rectangle.");
                lines.add("Drag inside the selection to move it. Ctrl+drag moves a copy; Ctrl+drag again stamps it and takes another.");
                lines.add(keyOr(keys, "edit.deselect", "Deselect") + " drops a moved selection and deselects.");
                lines.add(keyOr(keys, "edit.delete", "Delete") + " clears the selected pixels.");
                lines.add(keyOr(keys, "edit.selectAll", "Select all") + " selects the whole node.");
                lines.add("While a selection exists, every tool paints only inside it.");
            }
            case EYEDROPPER -> {
                lines.add("Click or drag to pick the color under the cursor.");
                lines.add("Alt+click does the same from any drawing tool.");
            }
            case LINE -> {
                lines.add("Drag from one end to the other.");
                lines.add("Right-drag draws with clear.");
            }
            case RECT -> {
                lines.add("Drag from corner to corner.");
                lines.add("Filled, in the second toolbar row, fills it in." + bound(keys, "view.rectFill", " Toggle: "));
                lines.add("Right-drag draws with clear.");
            }
            case ANCHOR -> {
                lines.add("Drag from a pixel to add an anchor aimed at the cursor.");
                lines.add("A node's first anchor becomes its root anchor. Shift+drag places or moves the root anchor.");
                lines.add("Drag an anchor to move it. Arrow keys point the selected one.");
                lines.add("Right-click or right-drag erases anchors. " + keyOr(keys, "edit.delete", "Delete") + " removes the selected one.");
                lines.add("Double-click an anchor to edit its settings.");
            }
        }
        if (tool.symmetric()) lines.add("Symmetry, in the second toolbar row, mirrors what you draw.");
        if (tool.symmetric()) lines.add("With a selection active, only pixels inside it change.");

        // Swing's HTML renderer scales CSS px by 1.3 unless W3C units are switched on, which labels cannot do.
        StringBuilder sb = new StringBuilder("<html><body style='width:" + Math.round(width / 1.3) + "px'>");
        for (String l : lines) sb.append("&bull;&nbsp;").append(esc(l)).append("<br>");
        sb.append("<br><font color='").append(muted()).append("'>Canvas: wheel zooms, middle-drag or Space+drag pans. ")
                .append(esc(keyOr(keys, "parent.prev", "") + " / " + keyOr(keys, "parent.next", "")))
                .append(" cycle the parent shown behind the node.</font></body></html>");
        return sb.toString();
    }

    /** The heading: the tool's name and its key. */
    static String title(Tool tool, Function<String, String> keys) {
        String k = keys.apply("tool." + tool.id);
        return k.isEmpty() ? tool.label : tool.label + " (" + k + ")";
    }

    private static String keyOr(Function<String, String> keys, String id, String fallback) {
        String k = keys.apply(id);
        return k.isEmpty() ? fallback : k;
    }

    private static String bound(Function<String, String> keys, String id, String prefix) {
        String k = keys.apply(id);
        return k.isEmpty() ? "" : prefix + k + ".";
    }

    private static String muted() {
        return String.format("#%06x", Draw.MUTED.getRGB() & 0xffffff);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
