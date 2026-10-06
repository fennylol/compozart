package critter.ui;

import critter.io.Json;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Every keyboard action, its default keys, and the user's rebindings.
 * Bindings live in the window's input map. Menu items only display them.
 */
public final class KeyMap {
    public record Def(String id, String label, List<KeyStroke> defaults, Runnable action, BooleanSupplier enabled) {
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, List<KeyStroke>> bindings = new HashMap<>();
    private final Map<String, List<JMenuItem>> menuItems = new HashMap<>();
    private JRootPane root;

    public static KeyStroke key(int code) {
        return KeyStroke.getKeyStroke(code, 0);
    }

    public static KeyStroke ctrl(int code) {
        return KeyStroke.getKeyStroke(code, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    public static KeyStroke ctrlShift(int code) {
        return KeyStroke.getKeyStroke(code, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK);
    }

    public void define(String id, String label, Runnable action, KeyStroke... defaults) {
        define(id, label, action, () -> true, defaults);
    }

    public void define(String id, String label, Runnable action, BooleanSupplier enabled, KeyStroke... defaults) {
        defs.put(id, new Def(id, label, List.of(defaults), action, enabled));
    }

    public Collection<Def> defs() {
        return defs.values();
    }

    public Def def(String id) {
        return defs.get(id);
    }

    public List<KeyStroke> bindings(String id) {
        List<KeyStroke> b = bindings.get(id);
        return b != null ? b : defs.get(id).defaults();
    }

    public void setBindings(Map<String, List<KeyStroke>> next) {
        bindings.clear();
        for (var e : next.entrySet()) {
            if (!defs.containsKey(e.getKey())) continue;
            if (!e.getValue().equals(defs.get(e.getKey()).defaults())) bindings.put(e.getKey(), List.copyOf(e.getValue()));
        }
        reinstall();
    }

    /** A menu item that runs an action and shows its first key, without registering the key itself. */
    public JMenuItem menuItem(String id) {
        Def d = defs.get(id);
        JMenuItem item = new DisplayOnlyMenuItem(d.label());
        item.addActionListener(e -> d.action().run());
        menuItems.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
        List<KeyStroke> b = bindings(id);
        item.setAccelerator(b.isEmpty() ? null : b.get(0));
        return item;
    }

    public void install(JRootPane root) {
        this.root = root;
        reinstall();
    }

    private void reinstall() {
        if (root == null) return;
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        for (KeyStroke ks : im.keys() == null ? new KeyStroke[0] : im.keys()) {
            if (im.get(ks) instanceof String s && s.startsWith("critter.")) im.remove(ks);
        }
        for (Def d : defs.values()) {
            String key = "critter." + d.id();
            am.put(key, new GuardedAction(d));
            for (KeyStroke ks : bindings(d.id())) im.put(ks, key);
        }
        for (var e : menuItems.entrySet()) {
            List<KeyStroke> b = bindings(e.getKey());
            for (JMenuItem item : e.getValue()) item.setAccelerator(b.isEmpty() ? null : b.get(0));
        }
    }

    /** Skips plain keys while a text field has focus, so typing "eyes" does not switch to the eraser. */
    private static final class GuardedAction extends AbstractAction {
        private final Def def;

        GuardedAction(Def def) {
            this.def = def;
        }

        @Override
        public boolean accept(Object sender) {
            if (!def.enabled().getAsBoolean()) return false;
            AWTEvent ev = EventQueue.getCurrentEvent();
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (ev instanceof KeyEvent ke && (focus instanceof JTextComponent || focus instanceof JSpinner)) {
                int mods = ke.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK);
                if (mods == 0) return false;
                if (focus instanceof JTextComponent && isTextEditingKey(ke)) return false;
            }
            return true;
        }

        private static boolean isTextEditingKey(KeyEvent ke) {
            int c = ke.getKeyCode();
            int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            boolean onlyCtrl = (ke.getModifiersEx() & ~InputEvent.SHIFT_DOWN_MASK) == ctrl;
            return onlyCtrl && (c == KeyEvent.VK_A || c == KeyEvent.VK_C || c == KeyEvent.VK_V || c == KeyEvent.VK_X
                    || c == KeyEvent.VK_LEFT || c == KeyEvent.VK_RIGHT || c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            def.action().run();
        }
    }

    /** Shows an accelerator in the menu without the menu also handling the key, which would double-fire. */
    private static final class DisplayOnlyMenuItem extends JMenuItem {
        DisplayOnlyMenuItem(String text) {
            super(text);
        }

        @Override
        protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
            if (condition == WHEN_IN_FOCUSED_WINDOW) return false;
            return super.processKeyBinding(ks, e, condition, pressed);
        }
    }

    // ---- display and storage ----

    public static String describe(KeyStroke ks) {
        String mods = InputEvent.getModifiersExText(ks.getModifiers());
        String key = KeyEvent.getKeyText(ks.getKeyCode());
        return mods.isEmpty() ? key : mods + "+" + key;
    }

    public static String describe(List<KeyStroke> keys) {
        StringJoiner j = new StringJoiner(", ");
        for (KeyStroke k : keys) j.add(describe(k));
        return j.toString();
    }

    private static Path file() {
        return Config.dir().resolve("keybindings.json");
    }

    /** Loads saved bindings. A missing or broken file leaves the defaults in place. */
    public String load() {
        Path f = file();
        if (!Files.exists(f)) return null;
        try {
            Map<String, Object> m = Json.obj(Json.parse(Files.readString(f, StandardCharsets.UTF_8)), "keybindings");
            Map<String, Object> b = Json.obj(m.get("bindings"), "bindings");
            Map<String, List<KeyStroke>> next = new HashMap<>();
            for (var e : b.entrySet()) {
                List<KeyStroke> keys = new ArrayList<>();
                for (Object o : Json.arr(e.getValue(), e.getKey())) {
                    KeyStroke ks = KeyStroke.getKeyStroke(String.valueOf(o));
                    if (ks != null) keys.add(ks);
                }
                next.put(e.getKey(), keys);
            }
            setBindings(next);
            return null;
        } catch (IOException | RuntimeException ex) {
            return "Could not read " + f + ": " + ex.getMessage();
        }
    }

    public void save() throws IOException {
        Map<String, Object> b = new TreeMap<>();
        for (var e : bindings.entrySet()) {
            List<Object> keys = new ArrayList<>();
            for (KeyStroke ks : e.getValue()) keys.add(ks.toString());
            b.put(e.getKey(), keys);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bindings", new LinkedHashMap<>(b));
        Files.createDirectories(file().getParent());
        Files.writeString(file(), Json.write(m), StandardCharsets.UTF_8);
    }
}
