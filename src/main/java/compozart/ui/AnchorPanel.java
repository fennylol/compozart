package compozart.ui;

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
    private static final String RANDOM = "(random)";
    private static final String UNNAMED = "(unnamed variant)";
    private static final String NONE = "(none)";

    private final Editor ed;
    private final AnchorMenus menus;
    private boolean updating;
    /** The anchor whose target and group the text fields are showing, so a late focus-lost commit edits the right one. */
    private NamedAnchor shown;

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel title = new JLabel();

    private final JComboBox<String> target = new JComboBox<>();
    private final JComboBox<String> variant = new JComboBox<>();
    private final JTextField group = new JTextField();
    private final JSpinner layer = new JSpinner(new SpinnerNumberModel(1, -999, 999, 1));
    private final JCheckBox mirrored = new JCheckBox("Mirrored");
    private final JCheckBox depthSet = new JCheckBox("Depth");
    private final JSpinner depth = new JSpinner(new SpinnerNumberModel(1, 0, 999, 1));
    private final JLabel depthNote = new JLabel();
    private final JComboBox<String> endVariant = new JComboBox<>();
    private final JLabel endLabel = new JLabel("End variant");
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
            b.setToolTipText("Point " + d.name().toLowerCase(Locale.ROOT));
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
        f.add("Points", dirButtons(rootDirs));
        f.add("Pixel", rootPos);
        JButton del = new JButton("Delete");
        del.addActionListener(e -> menus.delete());
        f.add((String) null, row(del));
        f.end();
        return f;
    }

    private JComponent namedCard() {
        Form f = new Form();
        target.setEditable(true);
        target.setToolTipText("The node name that attaches here");
        f.add("Target", target);
        variant.setToolTipText("Which variant attaches. Random picks one with the project seed.");
        f.add("Variant", variant);
        group.setToolTipText("Random anchors with the same target and group get the same pick");
        f.add("Group", group);
        layer.setToolTipText("The child's layer relative to this node");
        f.add("Layer +/-", layer);
        mirrored.setToolTipText("Reflect the child and its subtree across its root arrow");
        f.add((String) null, mirrored);
        JPanel depthRow = new JPanel(new BorderLayout(4, 0));
        depthRow.add(depth, BorderLayout.CENTER);
        f.add(depthSet, depthRow);
        depthNote.setForeground(Draw.MUTED);
        depthNote.setFont(depthNote.getFont().deriveFont(11f));
        f.add((String) null, depthNote);
        endVariant.setToolTipText("The variant used for the last repetition");
        f.add(endLabel, endVariant);
        f.add("Points", dirButtons(namedDirs));
        f.add("Pixel", namedPos);

        JButton open = new JButton("Open…");
        open.setToolTipText("Open the node that attaches here");
        open.addActionListener(e -> {
            NamedAnchor a = selected();
            if (a != null) menus.openAttached(a, open, 0, open.getHeight());
        });
        JButton up = new JButton("▲");
        up.setToolTipText("Draw earlier among same-layer siblings");
        up.addActionListener(e -> menus.reorder(-1));
        JButton down = new JButton("▼");
        down.setToolTipText("Draw later among same-layer siblings");
        down.addActionListener(e -> menus.reorder(1));
        JButton del = new JButton("Delete");
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
            String v = choiceToVariant((String) variant.getSelectedItem(), RANDOM);
            editAnchor("variant", a -> a.variant = v);
        });
        endVariant.addActionListener(e -> {
            if (updating) return;
            String v = choiceToVariant((String) endVariant.getSelectedItem(), NONE);
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

    private String choiceToVariant(String choice, String nullLabel) {
        if (choice == null || choice.equals(nullLabel)) return null;
        if (choice.equals(UNNAMED)) return "";
        if (choice.endsWith(" (missing)")) choice = choice.substring(0, choice.length() - 10);
        return choice;
    }

    private String variantToChoice(String v, String nullLabel) {
        if (v == null) return nullLabel;
        return v.isEmpty() ? UNNAMED : v;
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
                title.setText("Root anchor");
                RootAnchor r = n.root;
                rootDirs.get(r.dir()).setSelected(true);
                rootPos.setText("x " + r.x() + ", y " + r.y());
                cards.show(body, "root");
            } else {
                NamedAnchor a = n.anchors.get(sel);
                title.setText("Named anchor " + (sel + 1) + " of " + n.anchors.size());
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
        for (Node x : p.named(a.target)) variants.add(variantToChoice(x.variant, RANDOM));
        fillVariantCombo(variant, RANDOM, variants, a.variant);
        fillVariantCombo(endVariant, NONE, variants, a.endVariant);

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
            depthNote.setText("Only used when " + (a.target.isEmpty() ? "the target" : a.target) + " repeats.");
            depthNote.setForeground(Draw.MUTED);
        } else if (starts) {
            depthNote.setText(a.depth == null ? "Required: this anchor starts a repetition." : "Number of " + a.target + " in the chain.");
            depthNote.setForeground(a.depth == null ? Draw.WARNING : Draw.MUTED);
        } else {
            depthNote.setText("Inside the cycle: continues the count from above.");
            depthNote.setForeground(Draw.MUTED);
        }
        namedDirs.get(a.dir).setSelected(true);
        namedPos.setText("x " + a.x + ", y " + a.y);
    }

    private void fillVariantCombo(JComboBox<String> combo, String nullLabel, List<String> variants, String current) {
        List<String> items = new ArrayList<>();
        items.add(nullLabel);
        items.addAll(variants);
        String cur = variantToChoice(current, nullLabel);
        if (!items.contains(cur)) {
            cur = cur + " (missing)";
            items.add(cur);
        }
        combo.setModel(new DefaultComboBoxModel<>(items.toArray(String[]::new)));
        combo.setSelectedItem(cur);
    }
}
