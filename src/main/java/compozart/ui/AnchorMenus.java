package compozart.ui;

import compozart.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Anchor commands shared by the canvas, the properties panel and the keyboard. */
final class AnchorMenus {
    private final Editor ed;
    private final Consumer<String> createNodeNamed;

    AnchorMenus(Editor ed, Consumer<String> createNodeNamed) {
        this.ed = ed;
        this.createNodeNamed = createNodeNamed;
    }

    boolean hasAnchor() {
        return ed.node() != null && ed.anchor() != Editor.NO_ANCHOR;
    }

    Dir dir() {
        Node n = ed.node();
        int a = ed.anchor();
        if (n == null || a == Editor.NO_ANCHOR) return null;
        return a == Editor.ROOT_ANCHOR ? n.root.dir() : n.anchors.get(a).dir;
    }

    void point(Dir d) {
        Node n = ed.node();
        int a = ed.anchor();
        if (n == null || a == Editor.NO_ANCHOR || d == dir()) return;
        ed.edit(null, () -> {
            if (a == Editor.ROOT_ANCHOR) n.root = n.root.facing(d);
            else n.anchors.get(a).dir = d;
        });
    }

    void delete() {
        Node n = ed.node();
        int a = ed.anchor();
        if (n == null || a == Editor.NO_ANCHOR) return;
        ed.selectAnchor(Editor.NO_ANCHOR);
        ed.edit(null, () -> {
            if (a == Editor.ROOT_ANCHOR) n.root = null;
            else n.anchors.remove(a);
        });
    }

    /** Moves the selected named anchor earlier or later in the list, which changes same-layer draw order. */
    void reorder(int delta) {
        Node n = ed.node();
        int a = ed.anchor();
        if (n == null || a < 0) return;
        int b = a + delta;
        if (b < 0 || b >= n.anchors.size()) return;
        ed.edit(null, () -> n.anchors.add(b, n.anchors.remove(a)));
        ed.selectAnchor(b);
    }

    JPopupMenu anchorPopup(int which) {
        JPopupMenu m = new JPopupMenu();
        Node n = ed.node();
        if (n == null) return m;
        String[] names = {"Point up", "Point right", "Point down", "Point left"};
        Dir current = dir();
        for (Dir d : Dir.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(names[d.ordinal()], d == current);
            item.addActionListener(e -> point(d));
            m.add(item);
        }
        m.addSeparator();
        if (which >= 0) {
            m.add(attachedMenu(n.anchors.get(which)));
            JMenuItem up = new JMenuItem("Draw earlier");
            up.setEnabled(which > 0);
            up.addActionListener(e -> reorder(-1));
            JMenuItem down = new JMenuItem("Draw later");
            down.setEnabled(which < n.anchors.size() - 1);
            down.addActionListener(e -> reorder(1));
            m.add(up);
            m.add(down);
            m.addSeparator();
        }
        JMenuItem del = new JMenuItem("Delete anchor");
        del.addActionListener(e -> delete());
        m.add(del);
        return m;
    }

    /** A submenu listing every node that can attach at the anchor, plus a way to create one. */
    JMenu attachedMenu(NamedAnchor a) {
        JMenu menu = new JMenu("Open attached node");
        fillAttached(menu.getPopupMenu(), a);
        return menu;
    }

    private void fillAttached(JPopupMenu menu, NamedAnchor a) {
        List<Node> nodes = ed.project().named(a.target);
        for (Node node : nodes) {
            String label = node.variant.isEmpty() ? node.name + " (unnamed variant)" : node.name + " [" + node.variant + "]";
            if (a.variant != null && a.variant.equals(node.variant)) label += "  ← pinned";
            if (node.root == null) label += "  (no root anchor)";
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> ed.selectNode(node));
            menu.add(item);
        }
        if (!nodes.isEmpty()) menu.addSeparator();
        JMenuItem create = new JMenuItem(a.target.isEmpty() ? "Set a target name to create a node"
                : nodes.isEmpty() ? "Create node “" + a.target + "”" : "Create another “" + a.target + "” variant");
        create.setEnabled(!a.target.isEmpty());
        create.addActionListener(e -> createNodeNamed.accept(a.target));
        menu.add(create);
    }

    /** Opens the attached node directly when there is exactly one, otherwise shows a menu to choose. */
    void openAttached(NamedAnchor a, Component c, int x, int y) {
        List<Node> nodes = ed.project().named(a.target);
        if (a.variant != null) {
            Node pinned = ed.project().find(a.target, a.variant);
            if (pinned != null) {
                ed.selectNode(pinned);
                return;
            }
        }
        if (nodes.size() == 1) {
            ed.selectNode(nodes.get(0));
            return;
        }
        JPopupMenu m = new JPopupMenu();
        fillAttached(m, a);
        m.show(c, x, y);
    }
}
