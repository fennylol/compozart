package compozart.ui;

import compozart.text.L10n;

import compozart.io.ProjectIO;
import compozart.model.Palette;
import compozart.model.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** The shared palette: swatches, entry name, add, delete, reorder by dragging, and the color editor. */
final class PalettePanel extends JPanel {
    private final Editor ed;
    private final Swatches swatches = new Swatches();
    private final JTextField name = new JTextField(10);
    private final JLabel info = new JLabel();
    /** The entry the name field is showing, so a late focus-lost commit renames the right one. */
    private int nameFor = -1;
    /** The text the name field was loaded with; only a change from it counts as an edit. */
    private String nameLoaded = "";

    PalettePanel(Editor ed) {
        super(new BorderLayout());
        this.ed = ed;
        JLabel title = new JLabel(L10n.t("palette.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JButton add = new JButton("+");
        add.setToolTipText(L10n.t("palette.add.tip"));
        add.setMargin(new Insets(1, 6, 1, 6));
        add.addActionListener(e -> addColor());
        JButton del = new JButton(L10n.t("palette.delete"));
        del.setToolTipText(L10n.t("palette.delete.tip"));
        del.setMargin(new Insets(1, 6, 1, 6));
        del.addActionListener(e -> deleteColor());
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        head.setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));
        head.add(title);
        head.add(add);
        head.add(del);
        head.add(info);
        info.setForeground(Draw.MUTED);

        JScrollPane scroll = new JScrollPane(swatches, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        scroll.setMinimumSize(new Dimension(100, 50));

        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        nameRow.add(new JLabel(L10n.t("node.label.name")), BorderLayout.WEST);
        nameRow.add(name, BorderLayout.CENTER);
        name.addActionListener(e -> commitName());
        name.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitName();
            }
        });

        JPanel south = new JPanel(new BorderLayout());
        south.add(nameRow, BorderLayout.NORTH);
        JPanel editorWrap = new JPanel(new BorderLayout());
        editorWrap.add(new ColorEditor(ed), BorderLayout.NORTH);
        south.add(editorWrap, BorderLayout.CENTER);

        JPanel stack = new JPanel(new BorderLayout());
        stack.add(scroll, BorderLayout.NORTH);
        stack.add(south, BorderLayout.CENTER);
        add(head, BorderLayout.NORTH);
        add(stack, BorderLayout.CENTER);
        ed.addListener(what -> refresh());
        refresh();
    }

    private void refresh() {
        Project p = ed.project();
        int i = ed.color();
        if (nameFor != i) commitName();
        if (!name.hasFocus() || nameFor != i) {
            nameLoaded = p.palette.get(i).name();
            name.setText(nameLoaded);
        }
        nameFor = i;
        name.setEnabled(i > 0);
        info.setText("#" + i + " · " + ProjectIO.hexColor(p.palette.argb(i)) + " · " + p.palette.size() + "/" + Palette.MAX);
        swatches.revalidate();
        swatches.repaint();
    }

    private void commitName() {
        int i = nameFor;
        Palette pal = ed.project().palette;
        if (i <= 0 || i >= pal.size()) return;
        String n = name.getText().trim();
        Palette.Swatch s = pal.get(i);
        if (n.equals(nameLoaded) || n.equals(s.name())) return;
        nameLoaded = n;
        ed.edit(null, () -> pal.set(i, new Palette.Swatch(s.argb(), n)));
    }

    private void addColor() {
        Palette pal = ed.project().palette;
        if (pal.full()) {
            JOptionPane.showMessageDialog(this, L10n.t("palette.full", "max", Palette.MAX));
            return;
        }
        int argb = ed.color() > 0 ? pal.argb(ed.color()) : 0xff808080;
        int[] index = new int[1];
        ed.edit(null, () -> index[0] = pal.add(new Palette.Swatch(argb, "")));
        ed.selectColor(index[0]);
    }

    private void deleteColor() {
        int i = ed.color();
        Project p = ed.project();
        if (i <= 0) {
            JOptionPane.showMessageDialog(this, L10n.t("palette.clearLocked"));
            return;
        }
        int replacement;
        if (p.usesColor(i)) {
            JComboBox<Integer> to = new JComboBox<>();
            for (int k = 0; k < p.palette.size(); k++) if (k != i) to.addItem(k);
            to.setSelectedItem(0);
            to.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object v, int idx, boolean s, boolean f) {
                    super.getListCellRendererComponent(l, v, idx, s, f);
                    int k = (Integer) v;
                    Palette.Swatch sw = p.palette.get(k);
                    setText("#" + k + (sw.name().isEmpty() ? "" : " " + sw.name()));
                    setIcon(Draw.swatchIcon(sw.argb(), 14));
                    return this;
                }
            });
            JPanel form = new JPanel(new BorderLayout(0, 6));
            form.add(new JLabel(L10n.t("palette.remap", "index", i)), BorderLayout.NORTH);
            form.add(to, BorderLayout.CENTER);
            int r = JOptionPane.showConfirmDialog(this, form, L10n.t("palette.delete.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            replacement = (Integer) to.getSelectedItem();
        } else {
            replacement = 0;
        }
        ed.edit(null, () -> p.remapPixels(p.palette.remove(i, replacement)));
        ed.selectColor(Math.min(i, p.palette.size() - 1));
    }

    /** The swatch grid. Click to select, drag to reorder. */
    private final class Swatches extends JComponent implements Scrollable {
        private static final int CELL = 20, GAP = 3;
        private int dragFrom = -1, dragOver = -1;

        Swatches() {
            setToolTipText("");
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = indexAt(e.getPoint());
                    if (i < 0) return;
                    ed.selectColor(i);
                    dragFrom = i;
                    dragOver = i;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragFrom < 0) return;
                    dragOver = indexAt(e.getPoint());
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    int from = dragFrom, to = dragOver;
                    dragFrom = dragOver = -1;
                    repaint();
                    if (from > 0 && to > 0 && from != to) {
                        Project p = ed.project();
                        ed.edit(null, () -> p.remapPixels(p.palette.move(from, to)));
                        ed.selectColor(to);
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int columns() {
            return Math.max(1, (getWidth() + GAP) / (CELL + GAP));
        }

        private int indexAt(Point p) {
            int col = p.x / (CELL + GAP), row = p.y / (CELL + GAP);
            if (col >= columns()) return -1;
            int i = row * columns() + col;
            return i < ed.project().palette.size() ? i : -1;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int i = indexAt(e.getPoint());
            if (i < 0) return null;
            Palette.Swatch s = ed.project().palette.get(i);
            return "#" + i + (s.name().isEmpty() ? "" : " " + s.name()) + "  " + ProjectIO.hexColor(s.argb());
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getParent() == null ? 240 : getParent().getWidth();
            int cols = Math.max(1, (w + GAP) / (CELL + GAP));
            int rows = (ed.project().palette.size() + cols - 1) / cols;
            return new Dimension(w, rows * (CELL + GAP));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            Palette pal = ed.project().palette;
            int cols = columns();
            for (int i = 0; i < pal.size(); i++) {
                int x = (i % cols) * (CELL + GAP), y = (i / cols) * (CELL + GAP);
                Draw.checker(g, x, y, CELL, CELL, 5);
                g.setColor(new Color(pal.argb(i), true));
                g.fillRect(x, y, CELL, CELL);
                if (i == 0) {
                    g.setColor(Draw.PROBLEM);
                    g.drawLine(x + 2, y + CELL - 3, x + CELL - 3, y + 2);
                }
                if (i == ed.color()) {
                    g.setColor(Color.WHITE);
                    g.drawRect(x - 1, y - 1, CELL + 1, CELL + 1);
                    g.setColor(Color.BLACK);
                    g.drawRect(x, y, CELL - 1, CELL - 1);
                }
                if (i == dragOver && dragFrom > 0 && dragOver > 0 && dragOver != dragFrom) {
                    g.setColor(Draw.ACCENT);
                    g.fillRect(x - 2, y, 2, CELL);
                }
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            Dimension d = getPreferredSize();
            return new Dimension(d.width, Math.min(d.height, 8 * (CELL + GAP)));
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return CELL + GAP;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
