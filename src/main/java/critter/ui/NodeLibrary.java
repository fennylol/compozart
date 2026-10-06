package critter.ui;

import critter.model.Node;
import critter.model.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Every node in the project, attached or not. */
final class NodeLibrary extends JPanel {
    private static final int THUMB = 28;

    private final Editor ed;
    private final NodeActions actions;
    private final DefaultListModel<Node> model = new DefaultListModel<>();
    private final JList<Node> list = new JList<>(model);
    private boolean syncing;

    NodeLibrary(Editor ed, NodeActions actions) {
        super(new BorderLayout());
        this.ed = ed;
        this.actions = actions;
        JLabel title = new JLabel("Node library");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(button("+", "New node", () -> actions.create(null)));
        bar.add(button("Dup", "Duplicate as a new variant", actions::duplicate));
        bar.add(button("Edit", "Rename or change variant", actions::edit));
        bar.add(button("Size", "Resize", actions::resize));
        bar.add(button("Root", "Set as creature root", () -> actions.setRoot(ed.node())));
        bar.add(button("▲", "Move up", () -> actions.move(-1)));
        bar.add(button("▼", "Move down", () -> actions.move(1)));
        bar.add(button("Del", "Delete node", actions::delete));

        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.NORTH);
        north.add(bar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        add(new JScrollPane(list), BorderLayout.CENTER);
        list.addListSelectionListener(e -> {
            if (!syncing && !e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) ed.selectNode(list.getSelectedIndex());
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) actions.edit();
            }

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

    private static JButton button(String text, String tip, Runnable r) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setMargin(new Insets(1, 4, 1, 4));
        b.addActionListener(e -> r.run());
        return b;
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int i = list.locationToIndex(e.getPoint());
        if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) ed.selectNode(i);
        JPopupMenu m = new JPopupMenu();
        m.add(item("New node…", () -> actions.create(null)));
        if (ed.node() != null) {
            m.add(item("Duplicate as new variant", actions::duplicate));
            m.add(item("Edit name and variant…", actions::edit));
            m.add(item("Resize…", actions::resize));
            m.add(item("Set as root", () -> actions.setRoot(ed.node())));
            m.addSeparator();
            m.add(item("Delete", actions::delete));
        }
        m.show(list, e.getX(), e.getY());
    }

    private static JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> r.run());
        return i;
    }

    private void refresh() {
        syncing = true;
        try {
            Project p = ed.project();
            boolean same = model.size() == p.nodes.size();
            for (int i = 0; same && i < model.size(); i++) same = model.get(i) == p.nodes.get(i);
            if (!same) {
                model.clear();
                for (Node n : p.nodes) model.addElement(n);
            }
            if (list.getSelectedIndex() != ed.nodeIndex()) {
                if (ed.nodeIndex() >= 0) {
                    list.setSelectedIndex(ed.nodeIndex());
                    list.ensureIndexIsVisible(ed.nodeIndex());
                } else list.clearSelection();
            }
            list.repaint();
        } finally {
            syncing = false;
        }
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, value, index, sel, focus);
            Node n = (Node) value;
            Project p = ed.project();
            String muted = String.format("#%06x", Draw.MUTED.getRGB() & 0xffffff);
            StringBuilder sb = new StringBuilder("<html>").append(esc(n.name));
            if (!n.variant.isEmpty()) sb.append(" [").append(esc(n.variant)).append("]");
            sb.append("<br><font color='").append(muted).append("'>").append(n.size()).append("px");
            if (n.ref().equals(p.root)) sb.append(" · root");
            if (n.root == null && !n.ref().equals(p.root)) sb.append(" · no root anchor");
            sb.append("</font></html>");
            setText(sb.toString());
            setIcon(thumbnail(n, p));
            setIconTextGap(8);
            return this;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private static Icon thumbnail(Node n, Project p) {
        return Draw.nodeIcon(n, p.palette, THUMB);
    }
}
