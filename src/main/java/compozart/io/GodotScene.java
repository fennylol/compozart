package compozart.io;

import compozart.compose.Composition;
import compozart.compose.Instance;
import compozart.model.*;

import java.util.*;

/**
 * Writes a composed creature as a self-contained Godot 4 scene (.tscn).
 *
 * <p>The creature's root node is the scene root. Every attached part is a pivot node placed on its joint, the
 * pixel edge where its root anchor meets the parent's anchor, so rotating a pivot swings the part (and everything
 * attached to it) around that joint. Each pivot holds the part's sprite; rotation and mirroring live on the pivot.
 * Textures are embedded as Image sub-resources, so the file needs nothing else.
 */
public final class GodotScene {
    /** Godot's default Sprite3D pixel size: 100 pixels per unit. */
    public static final double DEFAULT_PIXEL_SIZE = 0.01;
    /** Depth between consecutive parts in 3D, in units, so overlapping sprites draw in creature order. */
    static final double DEPTH_STEP = 0.0005;

    private GodotScene() {
    }

    /** A 2D point in world pixels (y down). */
    private record P(double x, double y) {
    }

    /**
     * @param threeD    Node3D and Sprite3D instead of Node2D and Sprite2D
     * @param pixelSize 3D units per pixel; ignored in 2D
     */
    public static String write(Composition c, Palette palette, boolean threeD, double pixelSize) {
        if (c.root == null) throw new IllegalStateException("There is nothing to export: no root node is selected.");
        StringBuilder res = new StringBuilder();
        StringBuilder nodes = new StringBuilder();

        // one embedded texture per distinct node
        Map<Node, String> textures = new LinkedHashMap<>();
        for (Instance inst : c.instances) {
            if (textures.containsKey(inst.node)) continue;
            int k = textures.size() + 1;
            String image = "Image_" + k, texture = "ImageTexture_" + k;
            textures.put(inst.node, texture);
            res.append("[sub_resource type=\"Image\" id=\"").append(image).append("\"]\n");
            res.append("data = {\n\"data\": PackedByteArray(").append(rgba(inst.node, palette)).append("),\n")
                    .append("\"format\": \"RGBA8\",\n\"height\": ").append(inst.node.size()).append(",\n")
                    .append("\"mipmaps\": false,\n\"width\": ").append(inst.node.size()).append("\n}\n\n");
            res.append("[sub_resource type=\"ImageTexture\" id=\"").append(texture).append("\"]\n");
            res.append("image = SubResource(\"").append(image).append("\")\n\n");
        }

        // draw order: layers ascending, tree order within a layer; used for 3D depth
        List<Instance> order = new ArrayList<>(c.instances);
        order.sort(Comparator.comparingInt((Instance i) -> i.layer).thenComparingInt(i -> i.id));
        Map<Instance, Integer> drawIndex = new HashMap<>();
        for (int i = 0; i < order.size(); i++) drawIndex.put(order.get(i), i);

        Map<Instance, String> paths = new HashMap<>();
        Map<String, Set<String>> siblingNames = new HashMap<>();
        for (Instance inst : c.instances) {
            Sym s = inst.xform.sym();
            P joint = joint(inst);
            String name;
            String parentPath;
            if (inst.parent == null) {
                name = nodeName(inst.node);
                parentPath = null;
                paths.put(inst, ".");
            } else {
                parentPath = paths.get(inst.parent);
                name = unique(siblingNames.computeIfAbsent(parentPath, k -> new HashSet<>()), nodeName(inst.node));
                paths.put(inst, parentPath.equals(".") ? name : parentPath + "/" + name);
            }

            // the pivot, relative to the parent's pivot
            nodes.append("[node name=\"").append(name).append("\" type=\"").append(threeD ? "Node3D" : "Node2D").append('"');
            if (parentPath != null) nodes.append(" parent=\"").append(parentPath).append('"');
            nodes.append("]\n");
            if (inst.parent != null) {
                Sym ps = inst.parent.xform.sym();
                P pj = joint(inst.parent);
                Sym inv = ps.inverse();
                P local = apply(inv, joint.x - pj.x, joint.y - pj.y);
                Sym rel = s.then(inv);
                if (threeD) nodes.append("transform = ").append(transform3d(rel, local, pixelSize)).append('\n');
                else appendTransform2d(nodes, rel, local);
            }
            nodes.append('\n');

            // the sprite, unrotated inside its pivot: its top-left corner at S^-1 (T' - J)
            Node n = inst.node;
            double tx = inst.xform.tx() + 0.5 - (s.a() * 0.5 + s.b() * 0.5);
            double ty = inst.xform.ty() + 0.5 - (s.c() * 0.5 + s.d() * 0.5);
            P corner = apply(s.inverse(), tx - joint.x, ty - joint.y);
            String self = paths.get(inst);
            nodes.append("[node name=\"Sprite\" type=\"").append(threeD ? "Sprite3D" : "Sprite2D")
                    .append("\" parent=\"").append(self).append("\"]\n");
            if (threeD) {
                double cx = corner.x + n.size() / 2.0, cy = corner.y + n.size() / 2.0;
                nodes.append("transform = Transform3D(1, 0, 0, 0, 1, 0, 0, 0, 1, ").append(num(cx * pixelSize)).append(", ")
                        .append(num(-cy * pixelSize)).append(", ").append(num(drawIndex.get(inst) * DEPTH_STEP)).append(")\n");
                nodes.append("pixel_size = ").append(num(pixelSize)).append('\n');
                nodes.append("alpha_cut = 1\n");        // discard transparent pixels
                nodes.append("texture_filter = 0\n");   // nearest
            } else {
                nodes.append("z_index = ").append(inst.layer).append('\n');
                nodes.append("z_as_relative = false\n");
                nodes.append("texture_filter = 1\n");   // nearest
                nodes.append("position = Vector2(").append(num(corner.x)).append(", ").append(num(corner.y)).append(")\n");
                nodes.append("centered = false\n");
            }
            nodes.append("texture = SubResource(\"").append(textures.get(n)).append("\")\n\n");
        }

        int loadSteps = textures.size() * 2 + 1;
        return "[gd_scene load_steps=" + loadSteps + " format=3]\n\n" + res + nodes;
    }

    /**
     * The point a part pivots around, in world pixels. For the root, the center of its canvas. For a child,
     * the middle of the pixel edge where the parent's anchor and the child's root anchor meet.
     */
    private static P joint(Instance inst) {
        if (inst.parent == null) return new P(inst.node.size() / 2.0, inst.node.size() / 2.0);
        NamedAnchor a = inst.socket();
        Xform px = inst.parent.xform;
        int ax = px.x(a.x, a.y), ay = px.y(a.x, a.y);
        Dir d = px.apply(a.dir);
        return new P(ax + 0.5 + d.dx * 0.5, ay + 0.5 + d.dy * 0.5);
    }

    private static P apply(Sym s, double x, double y) {
        return new P(s.a() * x + s.b() * y, s.c() * x + s.d() * y);
    }

    /**
     * Node2D stores position, rotation and scale, applied as rotate(scale(p)). A mirror is written as
     * scale.x = -1, the usual way to flip a sprite in Godot, with the rotation that completes it.
     */
    private static void appendTransform2d(StringBuilder sb, Sym rel, P pos) {
        sb.append("position = Vector2(").append(num(pos.x)).append(", ").append(num(pos.y)).append(")\n");
        double angle = rel.mirrored() ? Math.atan2(-rel.c(), -rel.a()) : Math.atan2(rel.c(), rel.a());
        int quarter = Math.floorMod((int) Math.round(Math.toDegrees(angle) / 90), 4);
        // quarter turns written between -90 and 180 degrees, as Godot's inspector shows them
        if (quarter != 0) sb.append("rotation = ").append(num((quarter == 3 ? -1 : quarter) * Math.PI / 2)).append('\n');
        if (rel.mirrored()) sb.append("scale = Vector2(-1, 1)\n");
    }

    /** Pixels are y-down, 3D is y-up: the basis is F·S·F with F = diag(1, -1). Godot writes Basis row by row. */
    private static String transform3d(Sym rel, P pos, double pixelSize) {
        double r00 = rel.a(), r01 = -rel.b(), r10 = -rel.c(), r11 = rel.d();
        return "Transform3D(" + num(r00) + ", " + num(r01) + ", 0, " + num(r10) + ", " + num(r11) + ", 0, 0, 0, 1, "
                + num(pos.x * pixelSize) + ", " + num(-pos.y * pixelSize) + ", 0)";
    }

    /** RGBA8 bytes, row by row, as Godot's PackedByteArray text. */
    private static String rgba(Node n, Palette palette) {
        int s = n.size();
        StringBuilder sb = new StringBuilder(s * s * 16);
        byte[] px = n.pixels();
        for (int i = 0; i < px.length; i++) {
            int argb = palette.argb(px[i] & 0xff);
            if (i > 0) sb.append(", ");
            sb.append((argb >> 16) & 0xff).append(", ").append((argb >> 8) & 0xff).append(", ")
                    .append(argb & 0xff).append(", ").append(argb >>> 24);
        }
        return sb.toString();
    }

    /** Godot node names cannot contain . : @ / " % and must be unique among siblings. */
    static String nodeName(Node n) {
        String raw = n.variant.isEmpty() ? n.name : n.name + "_" + n.variant;
        String clean = raw.replaceAll("[.:@/\"%]", "_").trim();
        return clean.isEmpty() ? "part" : clean;
    }

    private static String unique(Set<String> taken, String base) {
        String name = base;
        for (int i = 2; !taken.add(name); i++) name = base + i;
        return name;
    }

    private static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
