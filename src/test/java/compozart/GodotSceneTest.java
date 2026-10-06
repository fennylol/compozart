package compozart;

import compozart.compose.Composer;
import compozart.compose.Composition;
import compozart.io.Exporter;
import compozart.io.GodotScene;
import compozart.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static compozart.test.Check.*;

public class GodotSceneTest {
    /** body with two eye sockets, the second mirrored, and an arm pointing right. */
    static Project creature() {
        Project p = new Project(Palette.catppuccinMocha());
        Node body = new Node("body", "", 8);
        body.set(0, 0, 1);
        NamedAnchor l = new NamedAnchor("eye", 2, 1, Dir.N);
        NamedAnchor r = new NamedAnchor("eye", 5, 1, Dir.N);
        r.mirrored = true;
        NamedAnchor arm = new NamedAnchor("arm.left/x", 7, 4, Dir.E);
        arm.layerModifier = -2;
        body.anchors.addAll(List.of(l, r, arm));
        Node eye = new Node("eye", "", 4);
        eye.root = new RootAnchor(1, 3, Dir.S);
        eye.set(3, 0, 5);
        Node a = new Node("arm.left/x", "", 4);
        a.root = new RootAnchor(0, 1, Dir.N); // turned to point west
        p.nodes.addAll(List.of(body, eye, a));
        p.root = body.ref();
        return p;
    }

    static List<String[]> nodes(String scene) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\[node name=\"([^\"]*)\" type=\"([^\"]*)\"(?: parent=\"([^\"]*)\")?\\]").matcher(scene);
        while (m.find()) out.add(new String[]{m.group(1), m.group(2), m.group(3)});
        return out;
    }

    public void testStructure2d() {
        Project p = creature();
        String s = GodotScene.write(Composer.compose(p), p.palette, false, 0.01);
        yes(s.startsWith("[gd_scene load_steps=7 format=3]"), "header counts 3 images and 3 textures");
        List<String[]> n = nodes(s);
        eq(8, n.size()); // 4 pivots, 4 sprites
        eq("body", n.get(0)[0]);
        eq("Node2D", n.get(0)[1]);
        eq(null, n.get(0)[2]);
        eq("Sprite2D", n.get(1)[1]);
        eq(".", n.get(1)[2]);
        // siblings get unique names; illegal characters are replaced
        Set<String> names = new HashSet<>();
        for (String[] x : n) if (".".equals(x[2]) && !x[0].equals("Sprite")) names.add(x[0]);
        eq(Set.of("eye", "eye2", "arm_left_x"), names);
        // every parent path exists
        Set<String> paths = new HashSet<>(List.of("."));
        for (String[] x : n) {
            if (x[2] == null) continue;
            yes(paths.contains(x[2]), "parent " + x[2] + " exists");
            paths.add(x[2].equals(".") ? x[0] : x[2] + "/" + x[0]);
        }
    }

    public void testPivotsAndMirroring2d() {
        Project p = creature();
        String s = GodotScene.write(Composer.compose(p), p.palette, false, 0.01);
        // root joint is the body's center (4, 4); the first eye's joint is the top edge of pixel (2, 1)
        yes(s.contains("[node name=\"eye\" type=\"Node2D\" parent=\".\"]\nposition = Vector2(-1.5, -3)\n"), "eye pivot on its joint");
        yes(s.contains("[node name=\"eye2\" type=\"Node2D\" parent=\".\"]\nposition = Vector2(1.5, -3)\nscale = Vector2(-1, 1)\n"),
                "mirrored eye uses a flip");
        yes(s.contains("rotation = -1.570796"), "the arm is turned a quarter, counterclockwise");
        yes(s.contains("z_index = -2"), "layers become z_index");
        yes(s.contains("texture_filter = 1"), "nearest filtering");
    }

    public void testTexturesEmbedRgba() {
        Project p = creature();
        String s = GodotScene.write(Composer.compose(p), p.palette, false, 0.01);
        Matcher m = Pattern.compile("PackedByteArray\\(([^)]*)\\)").matcher(s);
        yes(m.find(), "body texture");
        String[] bytes = m.group(1).split(", ");
        eq(8 * 8 * 4, bytes.length);
        eq(List.of("245", "224", "220", "255"), List.of(bytes).subList(0, 4)); // body (0,0) is rosewater
        eq(List.of("0", "0", "0", "0"), List.of(bytes).subList(4, 8));
    }

    public void testStructure3d() {
        Project p = creature();
        String s = GodotScene.write(Composer.compose(p), p.palette, true, 0.02);
        List<String[]> n = nodes(s);
        eq("Node3D", n.get(0)[1]);
        eq("Sprite3D", n.get(1)[1]);
        yes(s.contains("pixel_size = 0.02"), "pixel size");
        yes(s.contains("alpha_cut = 1"), "alpha cut");
        // the mirrored eye's basis flips x (rows: -1 0 0 / 0 1 0 / 0 0 1), at joint (1.5, -3) px = (0.03, 0.06)
        yes(s.contains("transform = Transform3D(-1, 0, 0, 0, 1, 0, 0, 0, 1, 0.03, 0.06, 0)"), "3D mirror and y-up position");
    }

    public void testExporterRoutes() {
        Project p = creature();
        Composition c = Composer.compose(p);
        yes(new String(Exporter.godot(Exporter.Format.GODOT_3D, c, p.palette, 0.01)).contains("Sprite3D"), "3D");
        yes(new String(Exporter.export(Exporter.Format.GODOT_2D, c, p.palette, 0, 1)).contains("Sprite2D"), "2D");
        no(Exporter.Format.GODOT_2D.scalable(), "scenes do not scale");
        throwsA(IllegalArgumentException.class, () -> Exporter.godot(Exporter.Format.FLAT_PNG, c, p.palette, 0.01));
    }
}
