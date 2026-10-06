package critter;

import critter.io.Json;
import critter.io.ProjectIO;
import critter.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static critter.test.Check.*;

public class IoTest {
    public void testJsonRoundTrip() {
        String text = "{\"a\": [1, 2.5, -3e2, true, false, null], \"s\": \"q\\\"\\\\\\n\\u00e9\", \"o\": {}, \"l\": []}";
        Object v = Json.parse(text);
        Map<String, Object> m = Json.obj(v, "root");
        eq(List.of(1L, 2.5, -300.0, true, false), Json.arr(m.get("a"), "a").subList(0, 5));
        eq(null, Json.arr(m.get("a"), "a").get(5));
        eq("q\"\\\né", m.get("s"));
        eq(v, Json.parse(Json.write(v)));
    }

    public void testJsonErrors() {
        throwsA(Json.ParseException.class, () -> Json.parse("{"));
        throwsA(Json.ParseException.class, () -> Json.parse("[1,]"));
        throwsA(Json.ParseException.class, () -> Json.parse("{} x"));
        throwsA(Json.ParseException.class, () -> Json.parse("\"\\q\""));
    }

    public void testJsonLongsSurvive() {
        eq(9007199254740993L, Json.parse("9007199254740993"));
    }

    static Project sample() {
        Project p = Project.createDefault();
        p.seed = 123456789L;
        p.palette.add(new Palette.Swatch(0x80123456, "glass"));
        Node body = p.nodes.get(0);
        body.set(0, 0, 1);
        body.set(31, 31, 27);
        NamedAnchor a = new NamedAnchor("tail", 5, 6, Dir.W);
        a.variant = "";
        a.group = "g";
        a.layerModifier = -2;
        a.mirrored = true;
        a.depth = 4;
        a.endVariant = "tip";
        body.anchors.add(a);
        body.anchors.add(new NamedAnchor("eye \"left\"", 1, 2, Dir.N));
        Node tail = new Node("tail", "spiky", 8);
        tail.root = new RootAnchor(7, 0, Dir.E);
        p.nodes.add(tail);
        return p;
    }

    public void testProjectRoundTrip() {
        Project p = sample();
        String json = ProjectIO.toJson(p);
        Project q = ProjectIO.fromJson(json);
        eq(json, ProjectIO.toJson(q));
        eq(p.seed, q.seed);
        eq(p.root, q.root);
        eq(28, q.palette.size());
        eq(0x80123456, q.palette.argb(27));
        Node body = q.find("body", "");
        eq(27, body.get(31, 31));
        NamedAnchor a = body.anchors.get(0);
        eq("", a.variant);
        eq("tip", a.endVariant);
        eq(Integer.valueOf(4), a.depth);
        eq(-2, a.layerModifier);
        yes(a.mirrored, "mirrored");
        NamedAnchor b = body.anchors.get(1);
        eq(null, b.variant);
        eq(null, b.endVariant);
        eq(null, b.depth);
        eq("eye \"left\"", b.target);
        eq(new RootAnchor(7, 0, Dir.E), q.find("tail", "spiky").root);
        eq(null, body.root);
    }

    public void testPixelRowsAreHex() {
        String json = ProjectIO.toJson(sample());
        yes(json.contains("\"01000000"), "first row starts with index 01");
        yes(json.contains("00001b\""), "last row ends with index 1b");
    }

    public void testRejectsBadFiles() {
        throwsA(IllegalArgumentException.class, () -> ProjectIO.fromJson("{\"format\": \"other\"}"));
        String json = ProjectIO.toJson(sample());
        throwsA(IllegalArgumentException.class, () -> ProjectIO.fromJson(json.replace("\"version\": 1", "\"version\": 99")));
        Project dup = sample();
        dup.nodes.add(new Node("tail", "spiky", 8));
        throwsA(IllegalArgumentException.class, () -> ProjectIO.fromJson(ProjectIO.toJson(dup)));
        // pixel index beyond the palette
        throwsA(IllegalArgumentException.class, () -> ProjectIO.fromJson(json.replace("00001b\"", "0000ff\"")));
    }

    public void testSaveAndLoadFile() throws Exception {
        Path dir = Files.createTempDirectory("critter");
        Path f = dir.resolve("x" + ProjectIO.EXTENSION);
        ProjectIO.save(sample(), f);
        ProjectIO.save(sample(), f); // overwrite
        eq(ProjectIO.toJson(sample()), ProjectIO.toJson(ProjectIO.load(f)));
        eq(1L, Files.list(dir).count());
        Files.delete(f);
        Files.delete(dir);
    }

    public void testColorParsing() {
        eq(0xff112233, ProjectIO.parseColor("#112233"));
        eq(0x44112233, ProjectIO.parseColor("11223344"));
        eq("#11223344", ProjectIO.hexColor(0x44112233));
        throwsA(IllegalArgumentException.class, () -> ProjectIO.parseColor("#12345"));
    }
}
