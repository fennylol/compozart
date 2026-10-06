package critter.ui;

import critter.model.Dir;
import critter.model.Node;
import critter.model.Palette;
import critter.model.Xform;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Drawing helpers shared by the views. */
final class Draw {
    static final Color ROOT_ANCHOR = new Color(0xfab387);
    static final Color NAMED_ANCHOR = new Color(0x89dceb);
    static final Color PROBLEM = new Color(0xf38ba8);
    static final Color WARNING = new Color(0xf9e2af);
    static final Color MUTED = new Color(0x9399b2);
    static final Color ACCENT = new Color(0xcba6f7);
    private static final Color CHECK_A = new Color(0x2a2a3a);
    private static final Color CHECK_B = new Color(0x34344a);

    private Draw() {
    }

    static BufferedImage image(byte[] indices, int w, int h, Palette palette) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[palette.size()];
        for (int i = 0; i < argb.length; i++) argb[i] = palette.argb(i);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = indices[y * w + x] & 0xff;
                row[x] = v < argb.length ? argb[v] : 0xffff00ff;
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    static BufferedImage image(Node n, Palette palette) {
        return image(n.pixels(), n.size(), n.size(), palette);
    }

    /**
     * The image-space transform for a node placed with {@code xf}: pixel (x, y) covers the unit square at
     * {@code xf(x, y)}. Rotations and reflections act about pixel centers.
     */
    static AffineTransform transform(Xform xf) {
        var s = xf.sym();
        AffineTransform at = new AffineTransform(s.a(), s.c(), s.b(), s.d(), 0, 0);
        double cx = s.a() * 0.5 + s.b() * 0.5, cy = s.c() * 0.5 + s.d() * 0.5;
        at.preConcatenate(AffineTransform.getTranslateInstance(xf.tx() + 0.5 - cx, xf.ty() + 0.5 - cy));
        return at;
    }

    static void checker(Graphics2D g, int x, int y, int w, int h, int cell) {
        Shape clip = g.getClip();
        g.clipRect(x, y, w, h);
        Rectangle vis = g.getClipBounds();
        g.setColor(CHECK_A);
        g.fillRect(x, y, w, h);
        g.setColor(CHECK_B);
        int x0 = Math.max(0, (vis.x - x) / cell), y0 = Math.max(0, (vis.y - y) / cell);
        int x1 = Math.min((w + cell - 1) / cell, (vis.x + vis.width - x) / cell + 1);
        int y1 = Math.min((h + cell - 1) / cell, (vis.y + vis.height - y) / cell + 1);
        for (int cy = y0; cy < y1; cy++) {
            for (int cx = x0; cx < x1; cx++) {
                if (((cx + cy) & 1) == 0) g.fillRect(x + cx * cell, y + cy * cell, cell, cell);
            }
        }
        g.setClip(clip);
    }

    /**
     * Draws an anchor arrow in screen space. It runs from behind the pixel center to the edge the anchor points at.
     * The tail extends back past the pixel so a small anchor is easy to spot.
     *
     * @param cx    screen x of the pixel center
     * @param cy    screen y of the pixel center
     * @param scale screen pixels per node pixel
     */
    static void arrow(Graphics2D g, double cx, double cy, Dir d, double scale, Color color, boolean selected) {
        double unit = Math.max(scale, 4);
        double tipX = cx + d.dx * scale * 0.5, tipY = cy + d.dy * scale * 0.5;
        double tailX = cx - d.dx * unit * 1.6, tailY = cy - d.dy * unit * 1.6;
        float w = (float) Math.max(1.5, Math.min(4, scale / 5));
        double head = Math.max(5, Math.min(14, scale * 0.45));
        double px = -d.dy, py = d.dx;
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(tipX, tipY);
        tri.lineTo(tipX - d.dx * head + px * head * 0.6, tipY - d.dy * head + py * head * 0.6);
        tri.lineTo(tipX - d.dx * head - px * head * 0.6, tipY - d.dy * head - py * head * 0.6);
        tri.closePath();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke old = g.getStroke();
        // dark outline for contrast against any pixel color
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(w + 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(tailX, tailY, tipX - d.dx * head * 0.5, tipY - d.dy * head * 0.5));
        g.draw(tri);
        if (selected) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(w + 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(tailX, tailY, tipX - d.dx * head * 0.5, tipY - d.dy * head * 0.5));
            g.draw(tri);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(tailX, tailY, tipX - d.dx * head * 0.5, tipY - d.dy * head * 0.5));
        g.fill(tri);
        double dot = Math.max(3, Math.min(8, scale * 0.3));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - dot / 2, cy - dot / 2, dot, dot));
        g.setStroke(old);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    /** Text with a dark outline, readable over any pixel color. */
    static void label(Graphics2D g, String text, double x, double y, Color color) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 200));
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) g.drawString(text, (float) x + dx, (float) y + dy);
        g.setColor(color);
        g.drawString(text, (float) x, (float) y);
    }

    /** A node thumbnail on a checkerboard, scaled to fit. */
    static Icon nodeIcon(Node n, Palette palette, int size) {
        BufferedImage img = image(n, palette);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                checker(g2, x, y, size, size, Math.max(2, size / 4));
                int s = n.size();
                int scale = Math.max(1, size / s);
                int w = Math.min(size, s * scale);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        s > size ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(img, x + (size - w) / 2, y + (size - w) / 2, w, w, null);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    static Icon swatchIcon(int argb, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g;
                checker(g2, x, y, size, size, Math.max(2, size / 4));
                g2.setColor(new Color(argb, true));
                g2.fillRect(x, y, size, size);
                g2.setColor(MUTED);
                g2.drawRect(x, y, size - 1, size - 1);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}
