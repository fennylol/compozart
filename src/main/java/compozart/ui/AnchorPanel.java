package compozart.ui;

import compozart.text.L10n;

import compozart.compose.NameGraph;
import compozart.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Properties of the selected anchor. */
final class AnchorPanel extends JPanel {

    private final Editor ed;
    private final AnchorMenus menus;
    private boolean updating;
    /** The anchor whose target and group the text fields are showing, so a late focus-lost commit edits the right one. */
    private NamedAnchor shown;

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel title = new JLabel();

    private final JComboBox<String> target = new JComboBox<>();
    private final JComboBox<Choice> variant = new JComboBox<>();
    private final JTextField group = new JTextField();
    private final JSpinner layer = new JSpinner(new SpinnerNumberModel(1, -999, 999, 1));
    private final JCheckBox mirrored = new JCheckBox(L10n.t("anchor.mirrored"));
    private final JCheckBox depthSet = new JCheckBox(L10n.t("anchor.depth"));
    private final JSpinner depth = new JSpinner(new SpinnerNumberModel(1, 0, 999, 1));
    private final JLabel depthNote = new JLabel();
    private final JComboBox<Choice> endVariant = new JComboBox<>();
    private final JLabel endLabel = new JLabel(L10n.t("anchor.endVariant"));
    private final JLabel namedPos = new JLabel();
    private final JLabel rootPos = new JLabel();
    private final Map<Dir, JToggleButton> namedDirs = new EnumMap<>(Dir.class);
    private final Map<Dir, JToggleButton> rootDirs = new EnumMap<>(Dir.class);

    private final java.util.function.Function<String, String> keyHint;
    private final JLabel help = new JLabel();
    private final JScrollPane scroll;

    AnchorPanel(Editor ed, AnchorMenus menus, java.util.function.Function<String, String> keyHint) {
        super(new BorderLayout());
        this.ed = ed;
        this.menus = menus;
        this.keyHint = keyHint;
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        add(title, BorderLayout.NORTH);
        scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        // The tool help wraps to the panel's width, so rewrap when the panel is resized.
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                refreshHelp();
            }
        });

        help.setVerticalAlignment(SwingConstants.TOP);
        help.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        body.add(help, "none");
        body.add(rootCard(), "root");
        body.add(namedCard(), "named");

        ed.addListener(what -> refresh());
        refresh();
    }

    // ---- layout ----

    private static final class Form extends JPanel {
        private int row;

        Form() {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(2, 8, 8, 8));
        }

        void add(String label, JComponent c) {
            add(label == null ? null : new JLabel(label), c);
        }

        void add(JComponent label, JComponent c) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++;
            g.insets = new Insets(2, 0, 2, 6);
            g.anchor = GridBagConstraints.WEST;
            if (label != null) {
                g.gridx = 0;
                add(label, g);
            }
            g.gridx = label == null ? 0 : 1;
            g.gridwidth = label == null ? 2 : 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(2, 0, 2, 0);
            add(c, g);
        }

        void end() {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++;
            g.weighty = 1;
            add(Box.createGlue(), g);
        }
    }

    private JPanel dirButtons(Map<Dir, JToggleButton> map) {
        JPanel p = new JPanel(new GridLayout(1, 4, 2, 0));
        String[] arrows = {"↑", "→", "↓", "←"};
        ButtonGroup bg = new ButtonGroup();
        for (Dir d : Dir.values()) {
            JToggleButton b = new JToggleButton(arrows[d.ordinal()]);
            b.setToolTipText(L10n.t(new String[]{"anchor.point.up", "anchor.point.right", "anchor.point.down", "anchor.point.left"}[d.ordinal()]));
            b.setMargin(new Insets(2, 4, 2, 4));
            b.addActionListener(e -> {
                if (!updating) menus.point(d);
            });
            bg.add(b);
            map.put(d, b);
            p.add(b);
        }
        return p;
    }

    private JComponent rootCard() {
        Form f = new Form();
        f.add(L10n.t("anchor.points"), dirButtons(rootDirs));
        f.add(L10n.t("anchor.pixel"), rootPos);
        JButton del = new JButton(L10n.t("anchor.deleteButton"));
        del.addActionListener(e -> menus.delete());
        f.add((String) null, row(del));
        f.end();
        return f;
    }

    private JComponent namedCard() {
        Form f = new Form();
        target.setEditable(true);
        target.setToolTipText(L10n.t("anchor.target.tip"));
        f.add(L10n.t("anchor.target"), target);
        variant.setToolTipText(L10n.t("anchor.variant.tip"));
        f.add(L10n.t("node.label.variant"), variant);
        group.setToolTipText(L10n.t("anchor.group.tip"));
        f.add(L10n.t("anchor.group"), group);
        layer.setToolTipText(L10n.t("anchor.layer.tip"));
        f.add(L10n.t("anchor.layer"), layer);
        mirrored.setToolTipText(L10n.t("anchor.mirrored.tip"));
        f.add((String) null, mirrored);
        JPanel depthRow = new JPanel(new BorderLayout(4, 0));
        depthRow.add(depth, BorderLayout.CENTER);
        f.add(depthSet, depthRow);
        depthNote.setForeground(Draw.MUTED);
        depthNote.setFont(depthNote.getFont().deriveFont(11f));
        f.add((String) null, depthNote);
        endVariant.setToolTipText(L10n.t("anchor.endVariant.tip"));
        f.add(endLabel, endVariant);
        f.add(L10n.t("anchor.points"), dirButtons(namedDirs));
        f.add(L10n.t("anchor.pixel"), namedPos);

        JButton open = new JButton(L10n.t("anchor.open"));
        open.setToolTipText(L10n.t("anchor.open.tip"));
        open.addActionListener(e -> {
            NamedAnchor a = selected();
            if (a != null) menus.openAttached(a, open, 0, open.getHeight());
        });
        JButton up = new JButton("▲");
        up.setToolTipText(L10n.t("anchor.drawEarlier.tip"));
        up.addActionListener(e -> menus.reorder(-1));
        JButton down = new JButton("▼");
        down.setToolTipText(L10n.t("anchor.drawLater.tip"));
        down.addActionListener(e -> menus.reorder(1));
        JButton del = new JButton(L10n.t("anchor.deleteButton"));
        del.addActionListener(e -> menus.delete());
        f.add((String) null, row(open, up, down, del));
        f.end();

        // edits
        JTextField targetEditor = (JTextField) target.getEditor().getEditorComponent();
        target.addActionListener(e -> commitTarget());
        targetEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitTarget();
            }
        });
        variant.addActionListener(e -> {
            if (updating) return;
            Choice c = (Choice) variant.getSelectedItem();
            String v = c == null ? null : c.value();
            editAnchor("variant", a -> a.variant = v);
        });
        endVariant.addActionListener(e -> {
            if (updating) return;
            Choice c = (Choice) endVariant.getSelectedItem();
            String v = c == null ? null : c.value();
            editAnchor("endVariant", a -> a.endVariant = v);
        });
        group.addActionListener(e -> commitGroup());
        group.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitGroup();
            }
        });
        layer.addChangeListener(e -> {
            if (!updating) editAnchor("layer", a -> a.layerModifier = (Integer) layer.getValue());
        });
        mirrored.addActionListener(e -> {
            if (!updating) editAnchor(null, a -> a.mirrored = mirrored.isSelected());
        });
        depthSet.addActionListener(e -> {
            if (!updating) editAnchor(null, a -> a.depth = depthSet.isSelected() ? (Integer) depth.getValue() : null);
        });
        depth.addChangeListener(e -> {
            if (!updating) editAnchor("depth", a -> a.depth = (Integer) depth.getValue());
        });
        return f;
    }

    private static JPanel row(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JComponent c : cs) {
            if (c instanceof AbstractButton b) b.setMargin(new Insets(2, 6, 2, 6));
            p.add(c);
        }
        return p;
    }

    // ---- editing ----

    private NamedAnchor selected() {
        Node n = ed.node();
        int a = ed.anchor();
        return n != null && a >= 0 && a < n.anchors.size() ? n.anchors.get(a) : null;
    }

    private void editAnchor(String field, Consumer<NamedAnchor> change) {
        NamedAnchor a = selected();
        if (a == null) return;
        String key = field == null ? null : "anchor:" + ed.nodeIndex() + ":" + ed.anchor() + ":" + field;
        ed.edit(key, () -> change.accept(a));
    }

    private void commitTarget() {
        if (!updating) commitText();
    }

    private void commitGroup() {
        if (!updating) commitText();
    }

    /** Writes the target and group fields to the anchor they were showing, if it still exists. */
    private void commitText() {
        NamedAnchor a = shown;
        if (a == null || !inProject(a)) return;
        String t = String.valueOf(target.getEditor().getItem()).trim();
        String g = group.getText().trim();
        if (t.equals(a.target) && g.equals(a.group)) return;
        ed.edit(null, () -> {
            a.target = t;
            a.group = g;
        });
    }

    private boolean inProject(NamedAnchor a) {
        for (Node n : ed.project().nodes) for (NamedAnchor x : n.anchors) if (x == a) return true;
        return false;
    }


    /** Called when the canvas creates an anchor, so the target name can be typed right away. */
    /** Puts the keyboard in the selected anchor's settings: the target name, or the root anchor's direction. */
    void focusSettings() {
        Node n = ed.node();
        int a = ed.anchor();
        if (n == null || a == Editor.NO_ANCHOR) return;
        if (a >= 0) {
            focusTarget();
            return;
        }
        SwingUtilities.invokeLater(() -> rootDirs.get(n.root.dir()).requestFocusInWindow());
    }

    void focusTarget() {
        SwingUtilities.invokeLater(() -> {
            JTextField tf = (JTextField) target.getEditor().getEditorComponent();
            tf.requestFocusInWindow();
            tf.selectAll();
        });
    }

    // ---- refresh ----

    /** Shows the current tool's name, key and instructions, wrapped to the panel. Called again after keys change. */
    void refreshHelp() {
        if (ed.node() != null && ed.anchor() != Editor.NO_ANCHOR) return;
        Tool t = ed.tool();
        title.setText(ToolHelp.title(t, keyHint));
        title.setIcon(PixelIcon.of(t.icon));
        // leave room for the vertical scrollbar, which appears once the text is long enough to need it
        int width = Math.max(120, scroll.getViewport().getWidth() - scroll.getVerticalScrollBar().getPreferredSize().width - 24);
        help.setText(ToolHelp.html(t, keyHint, width));
    }

    private void refresh() {
        NamedAnchor now = selected();
        if (shown != null && now != shown) {
            commitText(); // the selection moved away while a field still had unsaved text
            now = selected();
        }
        boolean switched = now != shown;
        updating = true;
        try {
            shown = now;
            Node n = ed.node();
            int sel = ed.anchor();
            title.setIcon(null);
            if (n == null || sel == Editor.NO_ANCHOR) {
                refreshHelp();
                cards.show(body, "none");
            } else if (sel == Editor.ROOT_ANCHOR) {
                title.setText(L10n.t("anchor.title.root"));
                RootAnchor r = n.root;
                rootDirs.get(r.dir()).setSelected(true);
                rootPos.setText(L10n.t("anchor.position", "x", r.x(), "y", r.y()));
                cards.show(body, "root");
            } else {
                NamedAnchor a = n.anchors.get(sel);
                title.setText(L10n.t("anchor.title.named", "index", sel + 1, "count", n.anchors.size()));
                refreshNamed(n, a, switched);
                cards.show(body, "named");
            }
        } finally {
            updating = false;
        }
    }

    private void refreshNamed(Node n, NamedAnchor a, boolean switched) {
        Project p = ed.project();
        JTextField tf = (JTextField) target.getEditor().getEditorComponent();
        if (!tf.hasFocus() || switched) {
            Set<String> names = new TreeSet<>();
            for (Node x : p.nodes) names.add(x.name);
            if (!tf.hasFocus()) target.setModel(new DefaultComboBoxModel<>(names.toArray(String[]::new)));
            target.getEditor().setItem(a.target);
        }

        List<String> variants = new ArrayList<>();
        for (Node x : p.named(a.target)) variants.add(x.variant);
        fillVariantCombo(variant, L10n.t("anchor.variant.random"), variants, a.variant);
        fillVariantCombo(endVariant, L10n.t("anchor.variant.none"), variants, a.endVariant);

        if (!group.hasFocus() || switched) group.setText(a.group);
        group.setEnabled(a.variant == null);
        layer.setValue(a.layerModifier);
        mirrored.setSelected(a.mirrored);

        NameGraph graph = new NameGraph(p);
        boolean repeats = graph.onCycle(a.target);
        boolean starts = graph.startsRepetition(n.name, a);
        depthSet.setSelected(a.depth != null);
        depth.setValue(a.depth == null ? 1 : a.depth);
        depth.setEnabled(a.depth != null);
        depthSet.setEnabled(repeats || a.depth != null);
        endVariant.setEnabled(repeats || a.endVariant != null);
        endLabel.setEnabled(endVariant.isEnabled());
        if (!repeats) {
            depthNote.setText(a.target.isEmpty() ? L10n.t("anchor.depth.unusedNoTarget") : L10n.t("anchor.depth.unused", "name", a.target));
            depthNote.setForeground(Draw.MUTED);
        } else if (starts) {
            depthNote.setText(a.depth == null ? L10n.t("anchor.depth.required") : L10n.t("anchor.depth.count", "name", a.target));
            depthNote.setForeground(a.depth == null ? Draw.WARNING : Draw.MUTED);
        } else {
            depthNote.setText(L10n.t("anchor.depth.inside"));
            depthNote.setForeground(Draw.MUTED);
        }
        namedDirs.get(a.dir).setSelected(true);
        namedPos.setText(L10n.t("anchor.position", "x", a.x, "y", a.y));
    }

    /**
     * A dropdown entry: the variant value it stands for (null for random or none) and its label.
     * Keeping the value next to the label means translated labels never need to be parsed back.
     */
    private record Choice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static String variantLabel(String v) {
        return v.isEmpty() ? L10n.t("anchor.variant.unnamed") : v;
    }

    private void fillVariantCombo(JComboBox<Choice> combo, String nullLabel, List<String> variants, String current) {
        List<Choice> items = new ArrayList<>();
        items.add(new Choice(null, nullLabel));
        for (String v : variants) items.add(new Choice(v, variantLabel(v)));
        Choice selected = null;
        for (Choice c : items) if (java.util.Objects.equals(c.value(), current)) selected = c;
        if (selected == null) {
            selected = new Choice(current, L10n.t("anchor.variant.missing", "name", variantLabel(current)));
            items.add(selected);
        }
        combo.setModel(new DefaultComboBoxModel<>(items.toArray(Choice[]::new)));
        combo.setSelectedItem(selected);
    }
}
