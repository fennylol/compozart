package compozart;

import compozart.io.Json;
import compozart.ui.KeyMap;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import static compozart.test.Check.*;

public class KeyMapTest {
    public void testNamesRoundTrip() {
        KeyStroke[] all = {
                KeyStroke.getKeyStroke(KeyEvent.VK_D, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_QUOTE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.ALT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
        };
        for (KeyStroke ks : all) eq(ks, KeyMap.parse(KeyMap.name(ks)));
        eq("ctrl+shift+Z", KeyMap.name(all[1]));
        eq("OPEN_BRACKET", KeyMap.name(all[2]));
    }

    public void testLenientParsing() {
        eq(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, 0), KeyMap.parse(","));
        eq(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0), KeyMap.parse("["));
        eq(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), KeyMap.parse(" Ctrl + a "));
        eq(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), KeyMap.parse("esc"));
        eq(null, KeyMap.parse("hyper+Q"));
        eq(null, KeyMap.parse("NOT_A_KEY"));
        eq(null, KeyMap.parse(""));
    }

    static KeyMap sample() {
        KeyMap k = new KeyMap();
        k.define("tool.pencil", "Pencil", () -> { }, KeyMap.key(KeyEvent.VK_D));
        k.define("edit.redo", "Redo", () -> { },
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        k.define("view.grid", "Grid", () -> { });
        return k;
    }

    public void testJsonListsEveryActionWithDefaults() {
        String text = Json.write(sample().toJson());
        Map<String, Object> m = Json.obj(Json.parse(text), "keys");
        eq(List.of("tool.pencil", "edit.redo", "view.grid"), List.copyOf(m.keySet()));
        eq(List.of("ctrl+shift+Z", "ctrl+Y"), m.get("edit.redo"));
        eq(List.of(), m.get("view.grid"));
        yes(text.contains("\"edit.redo\": [\"ctrl+shift+Z\", \"ctrl+Y\"]"), "short lists stay on one line");
    }

    public void testFromJsonAppliesAndReports() {
        KeyMap k = sample();
        Map<String, Object> m = Json.obj(Json.parse(
                "{\"tool.pencil\": [\"P\", \"bogus\"], \"view.grid\": [\"G\"], \"no.such\": [\"X\"]}"), "keys");
        List<String> problems = k.fromJson(m);
        eq(2, problems.size());
        eq(List.of(KeyMap.key(KeyEvent.VK_P)), k.bindings("tool.pencil"));
        eq(List.of(KeyMap.key(KeyEvent.VK_G)), k.bindings("view.grid"));
        eq(2, k.bindings("edit.redo").size()); // untouched actions keep their defaults
    }
}
