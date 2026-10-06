package compozart.ui;

import compozart.text.L10n;

import compozart.compose.ParentGhost;
import compozart.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Edits the selected node's pixels and anchors. */
final class CanvasView extends JComponent {
    private static final int[] ZOOMS = {1, 2, 3, 4, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64};

    private final Editor ed;
    private Consumer<String> status = s -> {
    };
    private Consumer<NamedAnchor> anchorCreated = a -> {
    };
    private AnchorMenus menus;
    /** Called when an anchor is double-clicked, so its settings can take focus. */
    private Runnable focusAnchorSettings = () -> {
    };

    private int zoom = 12;
    private double ox, oy;
    private boolean needsFit = true;
    private int lastIndex = -1;
    private int lastSize;

    private Point hover;
    // freehand strokes
    private boolean stroking;
    private int strokeIndex;
    private Point last;
    // line and rectangle
    private Point shapeStart, shapeEnd;
    private int shapeIndex;
    // selection
    private Rectangle sel;
    private byte[] floating;
    private Point selectStart, moveStart, moveOrigin;
    // anchor dragging
    private boolean anchorDrag, anchorMoved;
    // aiming a new anchor: it sits on the pixel where the press started and points toward the cursor
    private Point aimPixel;
    // right-dragging with the anchor tool erases anchors; the first erase records the undo step
    private boolean anchorErase, eraseRecorded;
    // panning
    private Point panStart;
    private double panOx, panOy;
    private boolean spaceDown;

    CanvasView(Editor ed) {
        this.ed = ed;
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(520, 520));
        ed.addListener(what -> {
            if (what.contains(Editor.Change.HISTORY)) {
                sel = null;
                floating = null;
                stroking = false;
                shapeStart = null;
            }
            Node n = ed.node();
            int size = n == null ? 0 : n.size();
            if (ed.nodeIndex() != lastIndex || size != lastSize) {
                if (ed.nodeIndex() != lastIndex) {
                    sel = null;
                    floating = null;
                }
                lastIndex = ed.nodeIndex();
                lastSize = size;
                needsFit = true;
            }
            updateStatus();
            repaint();
        });
        ed.addFlushHook(this::commitFloating);

        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                press(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                drag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hover = pixelAt(e.getPoint());
                updateStatus();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = null;
                updateStatus();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int step = e.getWheelRotation() < 0 ? 1 : -1;
                // Ctrl+wheel sizes the brush; the wheel alone zooms.
                if ((e.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0) {
                    ed.setBrushSize(ed.brushSize() + step);
                    updateStatus();
                } else zoomAt(e.getPoint(), step);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceDown = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceDown = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }

    void onStatus(Consumer<String> s) {
        status = s;
    }

    void onAnchorCreated(Consumer<NamedAnchor> c) {
        anchorCreated = c;
    }

    void onFocusAnchorSettings(Runnable r) {
        focusAnchorSettings = r;
    }

    void setMenus(AnchorMenus menus) {
        this.menus = menus;
    }

    // ---- view ----

    void fit() {
        Node n = ed.node();
        if (n == null || getWidth() == 0) return;
        int avail = Math.min(getWidth(), getHeight()) - 80;
        zoom = ZOOMS[0];
        for (int z : ZOOMS) if (z * n.size() <= avail) zoom = z;
        ox = Math.floor((getWidth() - zoom * n.size()) / 2.0);
        oy = Math.floor((getHeight() - zoom * n.size()) / 2.0);
        needsFit = false;
        repaint();
    }

    void zoomStep(int dir) {
        zoomAt(new Point(getWidth() / 2, getHeight() / 2), dir);
    }

    private void zoomAt(Point p, int dir) {
        int i = 0;
        while (i < ZOOMS.length - 1 && ZOOMS[i] < zoom) i++;
        int next = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, i + dir))];
        if (next == zoom) return;
        double px = (p.x - ox) / zoom, py = (p.y - oy) / zoom;
        zoom = next;
        ox = Math.round(p.x - px * zoom);
        oy = Math.round(p.y - py * zoom);
        updateStatus();
        repaint();
    }

    private Point pixelAt(Point screen) {
        return new Point((int) Math.floor((screen.x - ox) / zoom), (int) Math.floor((screen.y - oy) / zoom));
    }

    private void updateStatus() {
        Node n = ed.node();
        StringBuilder sb = new StringBuilder();
        if (n != null) {
            sb.append(L10n.t("status.node", "ref", n.ref(), "size", n.size()));
            if (hover != null && n.contains(hover.x, hover.y)) {
                int v = n.get(hover.x, hover.y);
                Palette.Swatch s = ed.project().palette.get(v);
                sb.append(" · ").append(L10n.t(s.name().isEmpty() ? "status.pixel" : "status.pixel.named",
                        "x", hover.x, "y", hover.y, "index", v, "name", s.name()));
            }
            sb.append(" · ");
        }
        if (ed.tool().brushed()) sb.append(L10n.t("status.brush", "size", ed.brushSize())).append(" · ");
        sb.append(L10n.t("status.zoom", "zoom", zoom));
        status.accept(sb.toString());
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g0) {
        if (needsFit && getWidth() > 0) fit();
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Draw.CANVAS);
        g.fillRect(0, 0, getWidth(), getHeight());
        Node n = ed.node();
        if (n == null) {
            Draw.label(g, L10n.t("canvas.noNode"), 20, 30, Draw.MUTED);
            g.dispose();
            return;
        }
        Palette pal = ed.project().palette;
        int s = n.size();
        int cw = s * zoom;
        int x0 = (int) ox, y0 = (int) oy;
        Draw.checker(g, x0, y0, cw, cw, Math.max(4, zoom));

        // parent background
        ParentGhost.Candidate ghost = ed.ghost();
        if (ghost != null) {
            Graphics2D gg = (Graphics2D) g.create();
            gg.translate(ox, oy);
            gg.scale(zoom, zoom);
            gg.transform(Draw.transform(ParentGhost.parentInChildFrame(ghost, n)));
            gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.drawImage(Draw.image(ghost.parent(), pal), 0, 0, null);
            gg.dispose();
        }

        // node pixels, with the floating selection and any shape preview
        byte[] px = n.pixels().clone();
        if (floating != null) stamp(px, s, floating, sel);
        if (shapeStart != null && shapeEnd != null) {
            for (Point p : shapePoints()) for (Point q : ed.symmetry().images(p.x, p.y, s)) {
                if (paintable(n, q.x, q.y)) px[q.y * s + q.x] = (byte) shapeIndex;
            }
        }
        BufferedImage img = Draw.image(px, s, s, pal);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, x0, y0, cw, cw, null);

        if (ed.grid() && zoom >= 6) {
            g.setColor(Draw.GRID);
            for (int i = 1; i < s; i++) {
                g.drawLine(x0 + i * zoom, y0, x0 + i * zoom, y0 + cw);
                g.drawLine(x0, y0 + i * zoom, x0 + cw, y0 + i * zoom);
            }
            if (s % 8 == 0 && s > 8) {
                g.setColor(Draw.GRID_MAJOR);
                for (int i = 8; i < s; i += 8) {
                    g.drawLine(x0 + i * zoom, y0, x0 + i * zoom, y0 + cw);
                    g.drawLine(x0, y0 + i * zoom, x0 + cw, y0 + i * zoom);
                }
            }
        }
        g.setColor(Draw.MUTED);
        g.drawRect(x0 - 1, y0 - 1, cw + 1, cw + 1);

        paintSymmetry(g, x0, y0, cw);
        paintAnchors(g, n);

        if (sel != null) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
            g.drawRect(x0 + sel.x * zoom, y0 + sel.y * zoom, sel.width * zoom, sel.height * zoom);
            g.setStroke(new BasicStroke());
        }
        if (hover != null && ed.tool().brushed() && !stroking) {
            paintBrushPreview(g, n, x0, y0);
        } else if (hover != null && ed.tool() != Tool.ANCHOR && ed.tool() != Tool.SELECT) {
            g.setColor(new Color(255, 255, 255, 120));
            for (Point q : ed.tool().symmetric() ? ed.symmetry().images(hover.x, hover.y, s) : Set.of(hover)) {
                if (n.contains(q.x, q.y)) g.drawRect(x0 + q.x * zoom, y0 + q.y * zoom, zoom - 1, zoom - 1);
            }
        }
        g.dispose();
    }

    /**
     * The brush footprint under the cursor, mirrored by symmetry and limited to paintable pixels.
     * Draw shows the selected color; the eraser (or the clear color) shows only the outline.
     * A dark-and-light outline keeps it visible on any background and for low-alpha colors.
     */
    private void paintBrushPreview(Graphics2D g, Node n, int x0, int y0) {
        int s = n.size();
        Set<Point> cells = new HashSet<>();
        for (Point c : Brush.stamp(List.of(hover), ed.brushSize())) {
            for (Point q : ed.symmetry().images(c.x, c.y, s)) if (paintable(n, q.x, q.y)) cells.add(q);
        }
        if (cells.isEmpty()) return;
        int index = ed.tool() == Tool.ERASER ? 0 : ed.color();
        if (index != 0) {
            g.setColor(new Color(ed.project().palette.argb(index), true));
            for (Point q : cells) g.fillRect(x0 + q.x * zoom, y0 + q.y * zoom, zoom, zoom);
        }
        java.awt.geom.Path2D.Float edges = new java.awt.geom.Path2D.Float();
        for (Point q : cells) {
            int px = x0 + q.x * zoom, py = y0 + q.y * zoom;
            if (!cells.contains(new Point(q.x, q.y - 1))) { edges.moveTo(px, py); edges.lineTo(px + zoom, py); }
            if (!cells.contains(new Point(q.x, q.y + 1))) { edges.moveTo(px, py + zoom); edges.lineTo(px + zoom, py + zoom); }
            if (!cells.contains(new Point(q.x - 1, q.y))) { edges.moveTo(px, py); edges.lineTo(px, py + zoom); }
            if (!cells.contains(new Point(q.x + 1, q.y))) { edges.moveTo(px + zoom, py); edges.lineTo(px + zoom, py + zoom); }
        }
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(0, 0, 0, 160));
        g.draw(edges);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 220));
        g.draw(edges);
        g.setStroke(old);
    }

    private void paintSymmetry(Graphics2D g, int x0, int y0, int cw) {
        Symmetry sym = ed.symmetry();
        if (sym == Symmetry.NONE) return;
        g.setColor(new Color(Draw.ACCENT.getRed(), Draw.ACCENT.getGreen(), Draw.ACCENT.getBlue(), 150));
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 4}, 0));
        int mid = cw / 2;
        if (sym == Symmetry.LEFT_RIGHT || sym == Symmetry.QUAD) g.drawLine(x0 + mid, y0, x0 + mid, y0 + cw);
        if (sym == Symmetry.TOP_BOTTOM || sym == Symmetry.QUAD) g.drawLine(x0, y0 + mid, x0 + cw, y0 + mid);
        if (sym == Symmetry.DIAGONAL_MAIN) g.drawLine(x0, y0, x0 + cw, y0 + cw);
        if (sym == Symmetry.DIAGONAL_ANTI) g.drawLine(x0 + cw, y0, x0, y0 + cw);
        g.setStroke(new BasicStroke());
    }

    private void paintAnchors(Graphics2D g, Node n) {
        int selA = ed.anchor();
        Font f = getFont().deriveFont(Font.PLAIN, 11f);
        g.setFont(f);
        for (int i = 0; i < n.anchors.size(); i++) {
            NamedAnchor a = n.anchors.get(i);
            double cx = ox + (a.x + 0.5) * zoom, cy = oy + (a.y + 0.5) * zoom;
            Draw.arrow(g, cx, cy, a.dir, zoom, Draw.NAMED_ANCHOR, selA == i);
            if (zoom >= 6 || selA == i) {
                String t = a.target.isEmpty() ? L10n.t("canvas.noTarget") : a.target;
                double unit = Math.max(zoom, 4) * 1.6;
                double lx = cx - a.dir.dx * unit, ly = cy - a.dir.dy * unit;
                Draw.label(g, t, lx + 4, ly + (a.dir.dy > 0 ? -4 : 12), Draw.NAMED_ANCHOR);
            }
        }
        if (n.root != null) {
            RootAnchor r = n.root;
            Draw.arrow(g, ox + (r.x() + 0.5) * zoom, oy + (r.y() + 0.5) * zoom, r.dir(), zoom, Draw.ROOT_ANCHOR,
                    selA == Editor.ROOT_ANCHOR);
        }
    }

    // ---- input ----

    private boolean panning(MouseEvent e) {
        return SwingUtilities.isMiddleMouseButton(e) || spaceDown && SwingUtilities.isLeftMouseButton(e);
    }

    private void press(MouseEvent e) {
        Node n = ed.node();
        if (panning(e)) {
            panStart = e.getPoint();
            panOx = ox;
            panOy = oy;
            return;
        }
        if (n == null) return;
        Point p = pixelAt(e.getPoint());
        boolean secondary = SwingUtilities.isRightMouseButton(e);
        Tool tool = ed.tool();
        if (e.isAltDown() && tool != Tool.ANCHOR && tool != Tool.SELECT) tool = Tool.EYEDROPPER;
        if (tool == Tool.DRAW && secondary) {
            drawToolAnchor(e, p);
            return;
        }
        if (tool == Tool.ERASER && secondary) {
            startAnchorErase(e, p);
            return;
        }
        switch (tool) {
            case DRAW, ERASER -> {
                ed.checkpoint(null);
                stroking = true;
                strokeIndex = tool == Tool.ERASER || secondary ? 0 : ed.color();
                last = p;
                plot(List.of(p));
            }
            case FILL -> fill(p, secondary ? 0 : ed.color());
            case EYEDROPPER -> pick(p);
            case LINE, RECT -> {
                shapeStart = shapeEnd = p;
                shapeIndex = secondary ? 0 : ed.color();
                repaint();
            }
            case SELECT -> selectPress(p, (e.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0);
            case ANCHOR -> anchorPress(e, p);
        }
    }

    private void drag(MouseEvent e) {
        Point p = pixelAt(e.getPoint());
        hover = p;
        if (panStart != null) {
            ox = panOx + e.getX() - panStart.x;
            oy = panOy + e.getY() - panStart.y;
            repaint();
            return;
        }
        Node n = ed.node();
        if (n == null) return;
        if (stroking) {
            plot(line(last, p));
            last = p;
        } else if (aimPixel != null) {
            aimTo(n, e.getPoint());
        } else if (anchorErase) {
            // follow the whole path so a fast drag does not skip anchors between mouse samples
            for (Point q : line(last, p)) {
                for (int a = anchorAtPixel(n, q); a != Editor.NO_ANCHOR; a = anchorAtPixel(n, q)) eraseAnchorAt(n, a);
            }
            last = p;
        } else if (shapeStart != null) {
            shapeEnd = p;
            repaint();
        } else if (ed.tool() == Tool.EYEDROPPER || e.isAltDown() && ed.tool() != Tool.ANCHOR && ed.tool() != Tool.SELECT) {
            pick(p);
        } else if (selectStart != null) {
            sel = rect(selectStart, clamp(p, n)).intersection(new Rectangle(0, 0, n.size(), n.size()));
            repaint();
        } else if (moveStart != null && floating != null) {
            sel.setLocation(moveOrigin.x + p.x - moveStart.x, moveOrigin.y + p.y - moveStart.y);
            repaint();
        } else if (anchorDrag) {
            anchorDragTo(n, p);
        }
        updateStatus();
    }

    private void release(MouseEvent e) {
        if (panStart != null) {
            panStart = null;
            return;
        }
        stroking = false;
        if (shapeStart != null) {
            Node n = ed.node();
            List<Point> pts = shapePoints();
            shapeStart = shapeEnd = null;
            if (n != null) {
                ed.checkpoint(null);
                plotWith(n, pts, shapeIndex);
                ed.changed();
            }
        }
        if (selectStart != null) {
            selectStart = null;
            if (sel != null && sel.isEmpty()) sel = null;
            repaint();
        }
        moveStart = null;
        anchorDrag = false;
        aimPixel = null;
        anchorErase = false;
    }

    // ---- pixel tools ----

    /** Paints a freehand stroke segment with the brush. */
    private void plot(List<Point> pts) {
        Node n = ed.node();
        if (plotWith(n, new ArrayList<>(Brush.stamp(pts, ed.brushSize())), strokeIndex)) ed.changed();
    }

    /** Pixels the drawing tools may change: inside the node, and inside the selection when there is one. */
    private boolean paintable(Node n, int x, int y) {
        return n.contains(x, y) && (sel == null || sel.contains(x, y));
    }

    private boolean plotWith(Node n, List<Point> pts, int index) {
        boolean any = false;
        for (Point p : pts) {
            for (Point q : ed.symmetry().images(p.x, p.y, n.size())) {
                if (paintable(n, q.x, q.y) && n.get(q.x, q.y) != index) {
                    n.set(q.x, q.y, index);
                    any = true;
                }
            }
        }
        return any;
    }

    private void fill(Point p, int index) {
        Node n = ed.node();
        Set<Point> seeds = ed.symmetry().images(p.x, p.y, n.size());
        boolean anyChange = seeds.stream().anyMatch(q -> paintable(n, q.x, q.y) && n.get(q.x, q.y) != index);
        if (!anyChange) return;
        ed.checkpoint(null);
        Rectangle bounds = sel != null ? sel : new Rectangle(0, 0, n.size(), n.size());
        for (Point q : seeds) if (paintable(n, q.x, q.y)) floodFill(n, q.x, q.y, index, bounds);
        ed.changed();
    }

    /** 4-connected flood fill that never leaves {@code bounds}. */
    static void floodFill(Node n, int x, int y, int index, Rectangle bounds) {
        int target = n.get(x, y);
        if (target == index) return;
        Rectangle r = bounds.intersection(new Rectangle(0, 0, n.size(), n.size()));
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] c = stack.pop();
            int cx = c[0], cy = c[1];
            if (!r.contains(cx, cy) || n.get(cx, cy) != target) continue;
            n.set(cx, cy, index);
            stack.push(new int[]{cx + 1, cy});
            stack.push(new int[]{cx - 1, cy});
            stack.push(new int[]{cx, cy + 1});
            stack.push(new int[]{cx, cy - 1});
        }
    }

    private void pick(Point p) {
        Node n = ed.node();
        if (n != null && n.contains(p.x, p.y)) ed.selectColor(n.get(p.x, p.y));
    }

    private List<Point> shapePoints() {
        if (shapeStart == null || shapeEnd == null) return List.of();
        if (ed.tool() == Tool.LINE) return line(shapeStart, shapeEnd);
        Rectangle r = rect(shapeStart, shapeEnd);
        List<Point> out = new ArrayList<>();
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                boolean edge = x == r.x || y == r.y || x == r.x + r.width - 1 || y == r.y + r.height - 1;
                if (edge || ed.rectFilled()) out.add(new Point(x, y));
            }
        }
        return out;
    }

    /** Bresenham line, both ends included. */
    static List<Point> line(Point a, Point b) {
        List<Point> out = new ArrayList<>();
        int x = a.x, y = a.y, dx = Math.abs(b.x - a.x), dy = -Math.abs(b.y - a.y);
        int sx = a.x < b.x ? 1 : -1, sy = a.y < b.y ? 1 : -1, err = dx + dy;
        while (true) {
            out.add(new Point(x, y));
            if (x == b.x && y == b.y) return out;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static Rectangle rect(Point a, Point b) {
        int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
        return new Rectangle(x, y, Math.abs(a.x - b.x) + 1, Math.abs(a.y - b.y) + 1);
    }

    private static Point clamp(Point p, Node n) {
        return new Point(Math.max(0, Math.min(n.size() - 1, p.x)), Math.max(0, Math.min(n.size() - 1, p.y)));
    }

    // ---- selection ----

    /**
     * Pressing inside the selection lifts it so it can be dragged. With Ctrl held it lifts a copy and leaves the
     * original in place; Ctrl-pressing a selection that is already floating stamps it and drags off another copy.
     */
    private void selectPress(Point p, boolean copy) {
        Node n = ed.node();
        if (sel != null && sel.contains(p)) {
            if (floating == null) {
                ed.checkpoint(null);
                floating = new byte[sel.width * sel.height];
                for (int y = 0; y < sel.height; y++) {
                    for (int x = 0; x < sel.width; x++) {
                        int sx = sel.x + x, sy = sel.y + y;
                        if (!n.contains(sx, sy)) continue;
                        floating[y * sel.width + x] = (byte) n.get(sx, sy);
                        if (!copy) n.set(sx, sy, 0);
                    }
                }
                ed.changed();
            } else if (copy) {
                // part of the same undo step as the lift, so undo can never lose the floating pixels
                stamp(n.pixels(), n.size(), floating, sel);
                ed.changed();
            }
            moveStart = p;
            moveOrigin = sel.getLocation();
            return;
        }
        commitFloating();
        sel = null;
        if (n.contains(p.x, p.y)) {
            selectStart = p;
            sel = new Rectangle(p.x, p.y, 1, 1);
        }
        repaint();
    }

    private static void stamp(byte[] px, int size, byte[] f, Rectangle r) {
        for (int y = 0; y < r.height; y++) {
            for (int x = 0; x < r.width; x++) {
                int tx = r.x + x, ty = r.y + y;
                byte v = f[y * r.width + x];
                if (v != 0 && tx >= 0 && ty >= 0 && tx < size && ty < size) px[ty * size + tx] = v;
            }
        }
    }

    /** Drops a moved selection onto the node. Part of the same undo step as lifting it. */
    void commitFloating() {
        if (floating == null) return;
        Node n = ed.node();
        byte[] f = floating;
        floating = null;
        if (n != null) {
            stamp(n.pixels(), n.size(), f, sel);
            ed.changed();
        }
    }

    boolean hasSelection() {
        return sel != null;
    }

    void deselect() {
        commitFloating();
        sel = null;
        shapeStart = null;
        repaint();
    }

    void deleteSelection() {
        Node n = ed.node();
        if (sel == null || n == null) return;
        if (floating != null) {
            floating = null;
            ed.changed();
        } else {
            ed.checkpoint(null);
            Rectangle r = sel.intersection(new Rectangle(0, 0, n.size(), n.size()));
            for (int y = r.y; y < r.y + r.height; y++) for (int x = r.x; x < r.x + r.width; x++) n.set(x, y, 0);
            ed.changed();
        }
        sel = null;
        repaint();
    }

    void selectAll() {
        Node n = ed.node();
        if (n == null) return;
        commitFloating();
        sel = new Rectangle(0, 0, n.size(), n.size());
        repaint();
    }

    // ---- anchors ----

    /**
     * The anchor under the mouse: first one whose pixel was clicked (named anchors first, last added on top,
     * then the root anchor), otherwise the nearest arrow within a few screen pixels of the click.
     */
    private int anchorAt(Node n, Point p, Point screen) {
        for (int i = n.anchors.size() - 1; i >= 0; i--) {
            NamedAnchor a = n.anchors.get(i);
            if (a.x == p.x && a.y == p.y) return i;
        }
        if (n.root != null && n.root.x() == p.x && n.root.y() == p.y) return Editor.ROOT_ANCHOR;
        double best = Math.max(5, zoom * 0.3);
        int hit = Editor.NO_ANCHOR;
        for (int i = n.anchors.size() - 1; i >= -1; i--) {
            int ax, ay;
            Dir d;
            if (i >= 0) {
                NamedAnchor a = n.anchors.get(i);
                ax = a.x;
                ay = a.y;
                d = a.dir;
            } else {
                if (n.root == null) break;
                ax = n.root.x();
                ay = n.root.y();
                d = n.root.dir();
            }
            double cx = ox + (ax + 0.5) * zoom, cy = oy + (ay + 0.5) * zoom;
            double unit = Math.max(zoom, 4) * 1.6;
            java.awt.geom.Line2D shaft = new java.awt.geom.Line2D.Double(cx - d.dx * unit, cy - d.dy * unit,
                    cx + d.dx * zoom * 0.5, cy + d.dy * zoom * 0.5);
            double dist = shaft.ptSegDist(screen.x, screen.y);
            if (dist < best) {
                best = dist;
                hit = i >= 0 ? i : Editor.ROOT_ANCHOR;
            }
        }
        return hit;
    }

    private void anchorPress(MouseEvent e, Point p) {
        Node n = ed.node();
        int hit = anchorAt(n, p, e.getPoint());
        if (SwingUtilities.isRightMouseButton(e)) {
            startAnchorErase(e, p);
            return;
        }
        if (e.isShiftDown()) {
            startAim(n, p, true);
            return;
        }
        if (hit != Editor.NO_ANCHOR) {
            ed.selectAnchor(hit);
            if (e.getClickCount() == 2) {
                focusAnchorSettings.run();
                return;
            }
            anchorDrag = true;
            anchorMoved = false;
            return;
        }
        if (!startAim(n, p, false)) ed.selectAnchor(Editor.NO_ANCHOR);
    }

    /**
     * Right-click with the draw tool: on an anchor's pixel it opens that anchor's menu,
     * anywhere else it adds an anchor (Shift for the root anchor) aimed by dragging.
     */
    private void drawToolAnchor(MouseEvent e, Point p) {
        Node n = ed.node();
        int hit = anchorAtPixel(n, p);
        if (hit != Editor.NO_ANCHOR && !e.isShiftDown()) {
            ed.selectAnchor(hit);
            if (menus != null) menus.anchorPopup(hit).show(this, e.getX(), e.getY());
            return;
        }
        startAim(n, p, e.isShiftDown());
    }

    /** The anchor whose own pixel is {@code p}: named anchors first, last added on top, then the root anchor. */
    private static int anchorAtPixel(Node n, Point p) {
        for (int i = n.anchors.size() - 1; i >= 0; i--) {
            if (n.anchors.get(i).x == p.x && n.anchors.get(i).y == p.y) return i;
        }
        if (n.root != null && n.root.x() == p.x && n.root.y() == p.y) return Editor.ROOT_ANCHOR;
        return Editor.NO_ANCHOR;
    }

    /**
     * Right-click with the anchor tool or the eraser: erases the anchor under the cursor (anywhere on its arrow),
     * then keeps erasing anchors along the drag.
     */
    private void startAnchorErase(MouseEvent e, Point p) {
        Node n = ed.node();
        anchorErase = true;
        eraseRecorded = false;
        last = p;
        eraseAnchorAt(n, anchorAt(n, p, e.getPoint()));
    }

    /** Removes one anchor during a right-click erase. A whole right-drag is one undo step. */
    private void eraseAnchorAt(Node n, int which) {
        if (which == Editor.NO_ANCHOR) return;
        if (!eraseRecorded) {
            ed.checkpoint(null);
            eraseRecorded = true;
        }
        ed.selectAnchor(Editor.NO_ANCHOR);
        if (which == Editor.ROOT_ANCHOR) n.root = null;
        else n.anchors.remove(which);
        ed.changed();
    }

    /**
     * Adds a named anchor, or places the root anchor, on the pressed pixel. Dragging then aims it.
     * Creation and aiming share one undo step.
     *
     * @return false when the pixel is outside the canvas
     */
    private boolean startAim(Node n, Point p, boolean root) {
        if (!n.contains(p.x, p.y)) return false;
        // A node's first anchor is its plug. The creature root is the exception: it ignores its root anchor.
        if (n.root == null && n.anchors.isEmpty() && !n.ref().equals(ed.project().root)) root = true;
        if (root) {
            ed.edit(null, () -> n.root = n.root == null ? new RootAnchor(p.x, p.y, Dir.S) : n.root.at(p.x, p.y));
            ed.selectAnchor(Editor.ROOT_ANCHOR);
        } else {
            NamedAnchor a = new NamedAnchor("", p.x, p.y, Dir.N);
            ed.edit(null, () -> n.anchors.add(a));
            ed.selectAnchor(n.anchors.size() - 1);
            anchorCreated.accept(a);
        }
        aimPixel = p;
        return true;
    }

    /** Points the anchor being placed toward the cursor, once the cursor leaves the anchor's pixel. */
    private void aimTo(Node n, Point screen) {
        int a = ed.anchor();
        if (a == Editor.NO_ANCHOR) return;
        double dx = screen.x - (ox + (aimPixel.x + 0.5) * zoom);
        double dy = screen.y - (oy + (aimPixel.y + 0.5) * zoom);
        if (Math.max(Math.abs(dx), Math.abs(dy)) < zoom * 0.5) return;
        Dir d = Dir.nearest(dx, dy);
        if (a == Editor.ROOT_ANCHOR) {
            if (n.root.dir() == d) return;
            n.root = n.root.facing(d);
        } else {
            if (n.anchors.get(a).dir == d) return;
            n.anchors.get(a).dir = d;
        }
        ed.changed();
    }

    private void anchorDragTo(Node n, Point p) {
        int a = ed.anchor();
        if (!n.contains(p.x, p.y) || a == Editor.NO_ANCHOR) return;
        int ax = a == Editor.ROOT_ANCHOR ? n.root.x() : n.anchors.get(a).x;
        int ay = a == Editor.ROOT_ANCHOR ? n.root.y() : n.anchors.get(a).y;
        if (ax == p.x && ay == p.y) return;
        if (!anchorMoved) {
            ed.checkpoint(null);
            anchorMoved = true;
        }
        if (a == Editor.ROOT_ANCHOR) n.root = n.root.at(p.x, p.y);
        else {
            n.anchors.get(a).x = p.x;
            n.anchors.get(a).y = p.y;
        }
        ed.changed();
    }
}
