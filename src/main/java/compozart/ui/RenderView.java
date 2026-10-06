package compozart.ui;

import compozart.text.L10n;

import compozart.compose.Composition;
import compozart.compose.Instance;
import compozart.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/** The whole creature composed, layer by layer. */
final class RenderView extends JPanel {
    private final Editor ed;
    private final Canvas canvas = new Canvas();
    private final JLabel seed = new JLabel();
    private final JCheckBox anchors = new JCheckBox(L10n.t("render.anchors"));
    private final JCheckBox outline = new JCheckBox(L10n.t("render.outline"), true);

    RenderView(Editor ed) {
        super(new BorderLayout());
        this.ed = ed;
        JLabel title = new JLabel(L10n.t("render.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JButton fit = new JButton(L10n.t("render.fit"));
        fit.addActionListener(e -> canvas.fit());
        JButton reroll = new JButton(L10n.t("render.reroll"));
        reroll.setToolTipText(L10n.t("render.reroll.tip"));
        reroll.addActionListener(e -> reroll());
        anchors.setToolTipText(L10n.t("render.anchors.tip"));
        outline.setToolTipText(L10n.t("render.outline.tip"));
        anchors.addActionListener(e -> canvas.repaint());
        outline.addActionListener(e -> canvas.repaint());
        seed.setForeground(Draw.MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.add(title);
        bar.add(fit);
        bar.add(reroll);
        bar.add(seed);
        bar.add(anchors);
        bar.add(outline);
        add(bar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        ed.addListener(what -> {
            if (what.contains(Editor.Change.HISTORY)) canvas.fitPending = true;
            seed.setText(L10n.t("render.seed", "value", ed.project().seed));
            canvas.repaint();
        });
        seed.setText(L10n.t("render.seed", "value", ed.project().seed));
    }

    void reroll() {
        long s = Project.newSeed();
        ed.edit(null, () -> ed.project().seed = s);
    }

    void fit() {
        canvas.fit();
    }

    private final class Canvas extends JComponent {
        private int zoom = 4;
        private double ox, oy;
        boolean fitPending = true;
        private Point panStart;
        private double panOx, panOy;

        Canvas() {
            setPreferredSize(new Dimension(360, 320));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        Composition c = ed.composition();
                        Instance inst = c.instanceAt((int) Math.floor((e.getX() - ox) / zoom), (int) Math.floor((e.getY() - oy) / zoom));
                        if (inst != null) {
                            ed.selectNode(inst.node);
                            return;
                        }
                    }
                    panStart = e.getPoint();
                    panOx = ox;
                    panOy = oy;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (panStart == null) return;
                    ox = panOx + e.getX() - panStart.x;
                    oy = panOy + e.getY() - panStart.y;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    panStart = null;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    int next = Math.max(1, Math.min(64, e.getWheelRotation() < 0 ? zoom + Math.max(1, zoom / 4) : zoom - Math.max(1, zoom / 5)));
                    double wx = (e.getX() - ox) / zoom, wy = (e.getY() - oy) / zoom;
                    zoom = next;
                    ox = Math.round(e.getX() - wx * zoom);
                    oy = Math.round(e.getY() - wy * zoom);
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
        }

        void fit() {
            Composition c = ed.composition();
            if (c.isEmpty() || getWidth() == 0) return;
            int pad = 24;
            zoom = Math.max(1, Math.min((getWidth() - pad) / c.width, (getHeight() - pad) / c.height));
            zoom = Math.min(zoom, 32);
            ox = Math.floor((getWidth() - c.width * zoom) / 2.0) - c.minX * zoom;
            oy = Math.floor((getHeight() - c.height * zoom) / 2.0) - c.minY * zoom;
            fitPending = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Composition c = ed.composition();
            if (fitPending && !c.isEmpty() && getWidth() > 0) fit();
            g.setColor(Draw.CANVAS);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (c.isEmpty()) {
                Draw.label(g, c.root == null ? L10n.t("render.noRoot") : L10n.t("render.empty"), 16, 24, Draw.MUTED);
                g.dispose();
                return;
            }
            int x0 = (int) (ox + c.minX * zoom), y0 = (int) (oy + c.minY * zoom);
            Draw.checker(g, x0, y0, c.width * zoom, c.height * zoom, Math.max(4, zoom));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(Draw.image(c.flat, c.width, c.height, ed.project().palette), x0, y0, c.width * zoom, c.height * zoom, null);

            Node current = ed.node();
            if (outline.isSelected() && current != null) {
                g.setColor(Draw.ACCENT);
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
                for (Instance inst : c.instances) {
                    if (inst.node != current) continue;
                    int s = inst.node.size() - 1;
                    int ax = inst.xform.x(0, 0), ay = inst.xform.y(0, 0), bx = inst.xform.x(s, s), by = inst.xform.y(s, s);
                    int minX = Math.min(ax, bx), minY = Math.min(ay, by);
                    g.drawRect((int) (ox + minX * zoom), (int) (oy + minY * zoom), (s + 1) * zoom - 1, (s + 1) * zoom - 1);
                }
                g.setStroke(new BasicStroke());
            }
            if (anchors.isSelected()) {
                for (Instance inst : c.instances) {
                    Xform xf = inst.xform;
                    for (NamedAnchor a : inst.node.anchors) {
                        Draw.arrow(g, ox + (xf.x(a.x, a.y) + 0.5) * zoom, oy + (xf.y(a.x, a.y) + 0.5) * zoom,
                                xf.apply(a.dir), zoom, Draw.NAMED_ANCHOR, false);
                    }
                    RootAnchor r = inst.node.root;
                    if (r != null && inst.parent != null) {
                        Draw.arrow(g, ox + (xf.x(r.x(), r.y()) + 0.5) * zoom, oy + (xf.y(r.x(), r.y()) + 0.5) * zoom,
                                xf.apply(r.dir()), zoom, Draw.ROOT_ANCHOR, false);
                    }
                }
            }
            String size = L10n.t("render.info", "width", c.width, "height", c.height,
                    "layers", L10n.plural("export.layers", c.layers.size()),
                    "parts", L10n.plural("export.parts", c.instances.size()), "zoom", zoom);
            g.setFont(getFont().deriveFont(11f));
            Draw.label(g, size, 8, getHeight() - 8, Draw.MUTED);
            g.dispose();
        }
    }
}
