package compozart.ui;

import compozart.text.L10n;

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
        String smaller = keys.apply("brush.smaller"), larger = keys.apply("brush.larger");
        String brush = smaller.isEmpty() || larger.isEmpty() ? L10n.t("help.brushSize")
                : L10n.t("help.brushSize.keys", "smaller", smaller, "larger", larger);
        switch (tool) {
            case DRAW -> {
                lines.add(L10n.t("help.draw.paint"));
                lines.add(brush);
                lines.add(L10n.t("help.draw.anchor"));
                lines.add(L10n.t("help.draw.menu"));
                lines.add(L10n.t("help.pickColor"));
            }
            case ERASER -> {
                lines.add(L10n.t("help.eraser.erase"));
                lines.add(L10n.t("help.eraser.anchors"));
                lines.add(brush);
            }
            case FILL -> {
                lines.add(L10n.t("help.fill.fill"));
                lines.add(L10n.t("help.fill.clear"));
                lines.add(L10n.t("help.pickColor"));
            }
            case SELECT -> {
                lines.add(L10n.t("help.select.drag"));
                lines.add(L10n.t("help.select.move"));
                lines.add(L10n.t("help.select.deselect", "key", keyOr(keys, "edit.deselect")));
                lines.add(L10n.t("help.select.delete", "key", keyOr(keys, "edit.delete")));
                lines.add(L10n.t("help.select.all", "key", keyOr(keys, "edit.selectAll")));
                lines.add(L10n.t("help.select.limit"));
            }
            case EYEDROPPER -> {
                lines.add(L10n.t("help.eyedropper.pick"));
                lines.add(L10n.t("help.eyedropper.alt"));
            }
            case LINE -> {
                lines.add(L10n.t("help.line.drag"));
                lines.add(L10n.t("help.clearDrag"));
            }
            case RECT -> {
                lines.add(L10n.t("help.rect.drag"));
                String k = keys.apply("view.rectFill");
                lines.add(k.isEmpty() ? L10n.t("help.rect.filled") : L10n.t("help.rect.filled.key", "key", k));
                lines.add(L10n.t("help.clearDrag"));
            }
            case ANCHOR -> {
                lines.add(L10n.t("help.anchor.add"));
                lines.add(L10n.t("help.anchor.first"));
                lines.add(L10n.t("help.anchor.move"));
                lines.add(L10n.t("help.anchor.erase", "key", keyOr(keys, "edit.delete")));
                lines.add(L10n.t("help.anchor.edit"));
            }
        }
        if (tool.symmetric()) lines.add(L10n.t("help.symmetry"));
        if (tool.symmetric()) lines.add(L10n.t("help.selectionLimit"));

        // Swing's HTML renderer scales CSS px by 1.3 unless W3C units are switched on, which labels cannot do.
        StringBuilder sb = new StringBuilder("<html><body style='width:" + Math.round(width / 1.3) + "px'>");
        for (String l : lines) sb.append("&bull;&nbsp;").append(esc(l)).append("<br>");
        sb.append("<br><font color='").append(muted()).append("'>")
                .append(esc(L10n.t("help.canvas", "prev", keyOr(keys, "parent.prev"), "next", keyOr(keys, "parent.next"))))
                .append("</font></body></html>");
        return sb.toString();
    }

    /** The heading: the tool's name and its key. */
    static String title(Tool tool, Function<String, String> keys) {
        String k = keys.apply("tool." + tool.id);
        return k.isEmpty() ? tool.label() : L10n.t("help.title", "tool", tool.label(), "key", k);
    }

    /** The action's key, or its name when it has no key. */
    private static String keyOr(Function<String, String> keys, String id) {
        String k = keys.apply(id);
        return k.isEmpty() ? L10n.t("action." + id) : k;
    }

    private static String muted() {
        return String.format("#%06x", Draw.MUTED.getRGB() & 0xffffff);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
