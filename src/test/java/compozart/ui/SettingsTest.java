package compozart.ui;

import compozart.io.Json;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static compozart.test.Check.*;

public class SettingsTest {
    static KeyStroke ctrl(int code) {
        return KeyStroke.getKeyStroke(code, InputEvent.CTRL_DOWN_MASK);
    }

    /** The actions involved in the version 1 to 2 change, with their current defaults. */
    static KeyMap keys() {
        KeyMap k = new KeyMap();
        k.define("edit.selectAll", "Select all", () -> { }, ctrl(KeyEvent.VK_A));
        k.define("node.new", "New node", () -> { }, KeyMap.shift(KeyEvent.VK_A));
        k.define("node.duplicate", "Duplicate", () -> { }, KeyMap.shift(KeyEvent.VK_D));
        k.define("node.resize", "Resize", () -> { }, ctrl(KeyEvent.VK_R));
        k.define("tool.draw", "Draw", () -> { }, KeyMap.key(KeyEvent.VK_D));
        return k;
    }

    public void testWritesDefaultsWhenMissing() throws Exception {
        Path dir = Files.createTempDirectory("settings");
        Path f = dir.resolve(Settings.FILE_NAME);
        eq(null, new Settings(f).load(keys()));
        Map<String, Object> m = Json.obj(Json.parse(Files.readString(f)), "file");
        eq(4L, m.get("version"));
        eq(List.of("shift+A"), Json.obj(m.get("keybindings"), "kb").get("node.new"));
        Files.delete(f);
        Files.delete(dir);
    }

    public void testVersionOneFileGetsNewDefaultsButKeepsCustomKeys() throws Exception {
        Path dir = Files.createTempDirectory("settings");
        Path f = dir.resolve(Settings.FILE_NAME);
        Files.writeString(f, """
                {"version": 1, "keybindings": {
                  "edit.selectAll": ["ctrl+A"],
                  "node.new": [],
                  "node.duplicate": ["F9"],
                  "node.resize": [],
                  "tool.pencil": ["P"]
                }}
                """);
        KeyMap k = keys();
        eq(null, new Settings(f).load(k));
        eq(List.of(ctrl(KeyEvent.VK_A)), k.bindings("edit.selectAll"));
        eq(List.of(KeyMap.shift(KeyEvent.VK_A)), k.bindings("node.new"));
        eq(List.of(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0)), k.bindings("node.duplicate"));
        eq(List.of(ctrl(KeyEvent.VK_R)), k.bindings("node.resize"));
        eq(List.of(KeyMap.key(KeyEvent.VK_P)), k.bindings("tool.draw")); // renamed in version 3, keys carried over
        eq(4L, Json.obj(Json.parse(Files.readString(f)), "file").get("version"));
        Files.delete(f);
        Files.delete(dir);
    }
}
