package critter.ui;

import critter.compose.Composition;
import critter.compose.Instance;
import critter.model.*;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** The expanded instance tree of the composed creature. */
final class CreatureTree extends JPanel {
    /** A placed instance. */
    record InstRow(Instance inst, String path) {
    }

    /** A named anchor where nothing attached. */
    record SlotRow(Instance owner, Instance.Slot slot, String path) {
    }

    private final Editor ed;
    private final NodeActions nodes;
    private final Consumer<String> createNodeNamed;
    private final JTree tree = new JTree(new DefaultTreeModel(null));
    private final JLabel warnings = new JLabel();
    private final Set<String> collapsed = new HashSet<>();
    private String lastKey;
    private Project lastProject;
    private boolean syncing;

    CreatureTree(Editor ed, NodeActions nodes, Consumer<String> createNodeNamed) {
        super(new BorderLayout());
        this.ed = ed;
        this.nodes = nodes;
        this.createNodeNamed = createNodeNamed;
        JLabel title = new JLabel("Creature");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        add(title, BorderLayout.NORTH);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setToggleClickCount(0);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        warnings.setForeground(Draw.WARNING);
        warnings.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(warnings, BorderLayout.SOUTH);

        tree.addTreeSelectionListener(e -> {
            if (syncing) return;
            Object row = rowOf(tree.getSelectionPath());
            if (row instanceof InstRow r) ed.selectNode(r.inst().node);
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                if (rowOf(e.getPath()) instanceof InstRow r) collapsed.remove(r.path());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent e) {
                if (rowOf(e.getPath()) instanceof InstRow r) collapsed.add(r.path());
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        ed.addListener(what -> refresh());
        refresh();
    }

    private static Object rowOf(TreePath p) {
        return p == null ? null : ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
    }

    // ---- building ----

    private void refresh() {
        Composition c = ed.composition();
        String key = c.structureKey() + "|" + ed.project().nodes.size();
        // Undo replaces the project, so rows would hold stale nodes even when the shape is unchanged.
        if (!key.equals(lastKey) || ed.project() != lastProject) {
            lastKey = key;
            lastProject = ed.project();
            rebuild(c);
        }
        syncSelection();
        tree.repaint();
        if (c.warnings.isEmpty()) {
            warnings.setText("");
            warnings.setVisible(false);
        } else {
            warnings.setText("<html>" + String.join("<br>", c.warnings.stream().map(CreatureTree::esc).toList()) + "</html>");
            warnings.setVisible(true);
        }
    }

    private void rebuild(Composition c) {
        syncing = true;
        try {
            DefaultMutableTreeNode root;
            if (c.root == null) {
                root = new DefaultMutableTreeNode("No root. Right-click a node in the library and choose Set as root.");
            } else {
                root = build(c.root, "r");
            }
            ((DefaultTreeModel) tree.getModel()).setRoot(root);
            for (int i = 0; i < tree.getRowCount(); i++) {
                TreePath p = tree.getPathForRow(i);
                if (rowOf(p) instanceof InstRow r && !collapsed.contains(r.path())) tree.expandRow(i);
            }
        } finally {
            syncing = false;
        }
    }

    private DefaultMutableTreeNode build(Instance inst, String path) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(new InstRow(inst, path));
        for (Instance.Slot s : inst.slots) {
            String childPath = path + "/" + s.anchorIndex;
            if (s.child != null) n.add(build(s.child, childPath));
            else n.add(new DefaultMutableTreeNode(new SlotRow(inst, s, childPath)));
        }
        return n;
    }

    /** Highlights the first row showing the node being edited, without feeding back into the editor. */
    private void syncSelection() {
        Node current = ed.node();
        Object sel = rowOf(tree.getSelectionPath());
        if (sel instanceof InstRow r && r.inst().node == current) return;
        syncing = true;
        try {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
            if (root == null) return;
            for (Enumeration<TreeNode> en = root.preorderEnumeration(); en.hasMoreElements(); ) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
                if (n.getUserObject() instanceof InstRow r && r.inst().node == current) {
                    TreePath p = new TreePath(n.getPath());
                    tree.setSelectionPath(p);
                    tree.scrollPathToVisible(p);
                    return;
                }
            }
            tree.clearSelection();
        } finally {
            syncing = false;
        }
    }

    // ---- rendering ----

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            setIcon(null);
            String muted = hex(Draw.MUTED);
            if (o instanceof InstRow r) {
                Instance inst = r.inst();
                StringBuilder sb = new StringBuilder("<html>");
                NamedAnchor socket = inst.socket();
                if (socket != null && !socket.target.equals(inst.node.name)) sb.append("<font color='").append(muted).append("'>").append(esc(socket.target)).append(": </font>");
                sb.append(esc(inst.node.name));
                if (!inst.node.variant.isEmpty()) sb.append(" [").append(esc(inst.node.variant)).append("]");
                sb.append("<font color='").append(muted).append("'>&nbsp;&nbsp;layer ").append(inst.layer);
                if (inst.xform.mirrored()) sb.append(" · mirrored");
                if (inst.parent == null) sb.append(" · root");
                sb.append("</font></html>");
                setText(sb.toString());
                setIcon(Draw.nodeIcon(inst.node, ed.project().palette, 16));
            } else if (o instanceof SlotRow r) {
                Instance.Slot s = r.slot();
                String why = s.problem != null ? s.problem : s.note != null ? s.note : "empty";
                String color = s.problem != null ? hex(Draw.PROBLEM) : muted;
                String target = s.anchor.target.isEmpty() ? "anchor " + (s.anchorIndex + 1) : s.anchor.target;
                setText("<html><font color='" + color + "'>" + esc(target) + ": " + esc(why) + "</font></html>");
            } else {
                setText(String.valueOf(o));
            }
            return this;
        }

        private String hex(Color c) {
            return String.format("#%06x", c.getRGB() & 0xffffff);
        }
    }

    // ---- context menu ----

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath p = tree.getPathForLocation(e.getX(), e.getY());
        if (p == null) return;
        Object row = rowOf(p);
        JPopupMenu m = new JPopupMenu();
        if (row instanceof InstRow r) {
            Instance inst = r.inst();
            JMenuItem open = new JMenuItem("Edit " + inst.node.ref());
            open.addActionListener(a -> ed.selectNode(inst.node));
            m.add(open);
            JMenuItem root = new JMenuItem("Set as root");
            root.setEnabled(inst.parent != null);
            root.addActionListener(a -> nodes.setRoot(inst.node));
            m.add(root);
            if (inst.parent != null) {
                m.addSeparator();
                anchorItems(m, inst.parent.node, inst.anchorIndex);
            }
        } else if (row instanceof SlotRow r) {
            anchorItems(m, r.owner().node, r.slot().anchorIndex);
            NamedAnchor a = r.slot().anchor;
            if (!a.target.isEmpty() && ed.project().named(a.target).isEmpty()) {
                m.addSeparator();
                JMenuItem create = new JMenuItem("Create node “" + a.target + "”");
                create.addActionListener(x -> createNodeNamed.accept(a.target));
                m.add(create);
            }
        } else {
            return;
        }
        m.show(tree, e.getX(), e.getY());
    }

    /** Menu items that edit the named anchor on {@code owner} at {@code index}. */
    private void anchorItems(JPopupMenu m, Node owner, int index) {
        NamedAnchor a = owner.anchors.get(index);
        JMenuItem go = new JMenuItem("Go to anchor in " + owner.ref());
        go.addActionListener(x -> {
            ed.selectNode(owner);
            ed.setTool(Tool.ANCHOR);
            ed.selectAnchor(index);
        });
        m.add(go);
        JCheckBoxMenuItem mir = new JCheckBoxMenuItem("Mirrored", a.mirrored);
        mir.addActionListener(x -> ed.edit(null, () -> a.mirrored = mir.isSelected()));
        m.add(mir);
        JMenuItem layer = new JMenuItem("Layer modifier (" + (a.layerModifier >= 0 ? "+" : "") + a.layerModifier + ")…");
        layer.addActionListener(x -> {
            String s = JOptionPane.showInputDialog(this, "Layer modifier for " + a.target + ":", a.layerModifier);
            if (s == null) return;
            try {
                int v = Integer.parseInt(s.trim().replace("+", ""));
                ed.edit(null, () -> a.layerModifier = v);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a whole number, like -1 or 2.");
            }
        });
        m.add(layer);
        JMenu variant = new JMenu("Variant");
        ButtonGroup bg = new ButtonGroup();
        JRadioButtonMenuItem random = new JRadioButtonMenuItem("Random", a.variant == null);
        random.addActionListener(x -> ed.edit(null, () -> a.variant = null));
        bg.add(random);
        variant.add(random);
        List<Node> options = ed.project().named(a.target);
        for (Node n : options) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(n.variant.isEmpty() ? "(unnamed variant)" : n.variant,
                    n.variant.equals(a.variant));
            item.addActionListener(x -> ed.edit(null, () -> a.variant = n.variant));
            bg.add(item);
            variant.add(item);
        }
        m.add(variant);
    }
}
