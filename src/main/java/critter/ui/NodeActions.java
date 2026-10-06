package critter.ui;

import critter.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Node library commands: create, duplicate, edit, resize, delete, reorder, set as root. */
final class NodeActions {
    private final Editor ed;
    private final Component parent;

    NodeActions(Editor ed, Component parent) {
        this.ed = ed;
        this.parent = parent;
    }

    private Project p() {
        return ed.project();
    }

    private String validate(String name, String variant, Node except) {
        if (name.isEmpty()) return "The name cannot be empty.";
        Node other = p().find(name, variant);
        if (other != null && other != except) return "A node " + new NodeRef(name, variant) + " already exists.";
        return null;
    }

    /** Creates a node, optionally with a preset name (from an anchor's target). */
    void create(String presetName) {
        String name = presetName != null ? presetName : p().uniqueName("part", "");
        String variant = presetName != null && !p().named(presetName).isEmpty() ? p().uniqueVariant(presetName, "variant") : "";
        JTextField nameF = new JTextField(name, 16);
        JTextField varF = new JTextField(variant, 16);
        JSpinner size = new JSpinner(new SpinnerNumberModel(Node.DEFAULT_SIZE, 1, Node.MAX_SIZE, 1));
        JCheckBox root = new JCheckBox("Add a root anchor (bottom center, pointing down)", true);
        JPanel form = form(new String[]{"Name", "Variant", "Size"}, nameF, varF, size);
        form.add(root, gbc(3, 0, 2));
        while (true) {
            if (!ask("New node", form, nameF)) return;
            String n = nameF.getText().trim(), v = varF.getText().trim();
            String err = validate(n, v, null);
            if (err != null) {
                error(err);
                continue;
            }
            int s = (Integer) size.getValue();
            Node node = new Node(n, v, s);
            if (root.isSelected()) node.root = new RootAnchor(s / 2, s - 1, Dir.S);
            int at = Math.min(p().nodes.size(), ed.nodeIndex() + 1);
            ed.edit(null, () -> {
                p().nodes.add(at, node);
                if (p().root == null || p().find(p().root) == null) p().root = node.ref();
            });
            ed.selectNode(node);
            return;
        }
    }

    /** Copies the current node as a new variant of the same name. */
    void duplicate() {
        Node n = ed.node();
        if (n == null) return;
        Node c = n.copy();
        c.variant = p().uniqueVariant(n.name, n.variant.isEmpty() ? "copy" : n.variant + " copy");
        int at = ed.nodeIndex() + 1;
        ed.edit(null, () -> p().nodes.add(at, c));
        ed.selectNode(c);
    }

    void edit() {
        Node n = ed.node();
        if (n == null) return;
        JTextField nameF = new JTextField(n.name, 16);
        JTextField varF = new JTextField(n.variant, 16);
        JPanel form = form(new String[]{"Name", "Variant"}, nameF, varF);
        while (true) {
            if (!ask("Edit node", form, nameF)) return;
            String name = nameF.getText().trim(), variant = varF.getText().trim();
            String err = validate(name, variant, n);
            if (err != null) {
                error(err);
                continue;
            }
            String oldName = n.name;
            NodeRef oldRef = n.ref();
            boolean renamed = !name.equals(oldName);
            boolean retarget = false;
            if (renamed) {
                boolean othersKeepName = p().named(oldName).size() > 1;
                boolean targeted = p().nodes.stream().anyMatch(x -> x.anchors.stream().anyMatch(a -> a.target.equals(oldName)));
                if (targeted && !othersKeepName) {
                    int r = JOptionPane.showConfirmDialog(parent,
                            "Anchors target “" + oldName + "”. Point them at “" + name + "” too?",
                            "Rename", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return;
                    retarget = r == JOptionPane.YES_OPTION;
                }
            }
            boolean doRetarget = retarget;
            ed.edit(null, () -> {
                if (doRetarget) p().renameTarget(oldName, name);
                if (oldRef.equals(p().root)) p().root = new NodeRef(name, variant);
                // Anchors that still point at this node keep pointing at it when its variant name changes.
                boolean stillTargeted = !renamed || doRetarget;
                if (stillTargeted) {
                    for (Node x : p().nodes) {
                        for (NamedAnchor a : x.anchors) {
                            if (!a.target.equals(name)) continue;
                            if (oldRef.variant().equals(a.variant)) a.variant = variant;
                            if (oldRef.variant().equals(a.endVariant)) a.endVariant = variant;
                        }
                    }
                }
                n.name = name;
                n.variant = variant;
            });
            return;
        }
    }

    void resize() {
        Node n = ed.node();
        if (n == null) return;
        ed.flush();
        JSpinner size = new JSpinner(new SpinnerNumberModel(n.size(), 1, Node.MAX_SIZE, 1));
        JToggleButton[] fix = new JToggleButton[9];
        ButtonGroup bg = new ButtonGroup();
        JPanel grid = new JPanel(new GridLayout(3, 3, 2, 2));
        String[] arrows = {"↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘"};
        for (int i = 0; i < 9; i++) {
            fix[i] = new JToggleButton(arrows[i]);
            fix[i].setMargin(new Insets(2, 6, 2, 6));
            bg.add(fix[i]);
            grid.add(fix[i]);
        }
        fix[4].setSelected(true);
        JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridWrap.add(grid);
        JPanel form = form(new String[]{"Size", "Keep fixed"}, size, gridWrap);
        if (!ask("Resize " + n.ref(), form, size)) return;
        int s = (Integer) size.getValue();
        int k = 0;
        for (int i = 0; i < 9; i++) if (fix[i].isSelected()) k = i;
        int fx = k % 3, fy = k / 3;
        if (s == n.size()) return;
        int lost = n.anchorsLostByResize(s, fx, fy);
        if (lost > 0) {
            int r = JOptionPane.showConfirmDialog(parent, lost + (lost == 1 ? " anchor falls" : " anchors fall")
                    + " outside the new size and will be removed. Resize anyway?", "Resize", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) return;
        }
        ed.edit(null, () -> n.resize(s, fx, fy));
    }

    void delete() {
        Node n = ed.node();
        if (n == null) return;
        int r = JOptionPane.showConfirmDialog(parent, "Delete " + n.ref() + "?", "Delete node", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;
        int i = ed.nodeIndex();
        ed.edit(null, () -> {
            p().nodes.remove(i);
            if (n.ref().equals(p().root)) p().root = null;
        });
    }

    void move(int delta) {
        int i = ed.nodeIndex(), j = i + delta;
        List<Node> nodes = p().nodes;
        if (i < 0 || j < 0 || j >= nodes.size()) return;
        ed.edit(null, () -> nodes.add(j, nodes.remove(i)));
        ed.selectNode(j);
    }

    void setRoot(Node n) {
        if (n == null || n.ref().equals(p().root)) return;
        ed.edit(null, () -> p().root = n.ref());
    }

    // ---- dialog helpers ----

    private boolean ask(String title, JComponent form, JComponent focus) {
        JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog d = pane.createDialog(parent, title);
        d.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                focus.requestFocusInWindow();
                if (focus instanceof JTextField tf) tf.selectAll();
            }
        });
        d.setVisible(true);
        d.dispose();
        return Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue());
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Node", JOptionPane.WARNING_MESSAGE);
    }

    static JPanel form(String[] labels, JComponent... fields) {
        JPanel p = new JPanel(new GridBagLayout());
        for (int i = 0; i < labels.length; i++) {
            p.add(new JLabel(labels[i]), gbc(i, 0, 1));
            GridBagConstraints g = gbc(i, 1, 1);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            p.add(fields[i], g);
        }
        return p;
    }

    static GridBagConstraints gbc(int row, int col, int width) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.gridx = col;
        g.gridwidth = width;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(3, 3, 3, 6);
        return g;
    }
}
