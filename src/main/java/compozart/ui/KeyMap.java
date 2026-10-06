package compozart.ui;

import compozart.text.L10n;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Every keyboard action, its default keys, and the user's rebindings.
 * Bindings live in the window's input map. Menu items only display them.
 */
public final class KeyMap {
    /** An action. Its label is looked up each time it is shown, so it follows the current language. */
    public record Def(String id, java.util.function.Supplier<String> labelText, List<KeyStroke> defaults, Runnable action,
                      BooleanSupplier enabled) {
        public String label() {
            return labelText.get();
        }
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, List<KeyStroke>> bindings = new HashMap<>();
    private final Map<String, List<JMenuItem>> menuItems = new HashMap<>();
    private JRootPane root;

    public static KeyStroke key(int code) {
        return KeyStroke.getKeyStroke(code, 0);
    }

    public static KeyStroke shift(int code) {
        return KeyStroke.getKeyStroke(code, InputEvent.SHIFT_DOWN_MASK);
    }

    public static KeyStroke ctrl(int code) {
        return KeyStroke.getKeyStroke(code, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    public static KeyStroke ctrlShift(int code) {
        return KeyStroke.getKeyStroke(code, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK);
    }

    /** Defines an action whose label is the language file's text for {@code labelKey}. */
    public void define(String id, String labelKey, Runnable action, KeyStroke... defaults) {
        define(id, labelKey, action, () -> true, defaults);
    }

    public void define(String id, String labelKey, Runnable action, BooleanSupplier enabled, KeyStroke... defaults) {
        defs.put(id, new Def(id, () -> L10n.t(labelKey), List.of(defaults), action, enabled));
    }

    /** Defines an action whose label is computed, for labels with values in them. */
    public void define(String id, java.util.function.Supplier<String> label, Runnable action, KeyStroke... defaults) {
        defs.put(id, new Def(id, label, List.of(defaults), action, () -> true));
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
            if (im.get(ks) instanceof String s && s.startsWith("compozart.")) im.remove(ks);
        }
        for (Def d : defs.values()) {
            String key = "compozart." + d.id();
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

    /** Punctuation keys read better as the character than as Java's names ("Open Bracket"). */
    private static final Map<Integer, String> PUNCTUATION = Map.ofEntries(
            Map.entry(KeyEvent.VK_COMMA, ","), Map.entry(KeyEvent.VK_PERIOD, "."), Map.entry(KeyEvent.VK_SEMICOLON, ";"),
            Map.entry(KeyEvent.VK_QUOTE, "'"), Map.entry(KeyEvent.VK_OPEN_BRACKET, "["), Map.entry(KeyEvent.VK_CLOSE_BRACKET, "]"),
            Map.entry(KeyEvent.VK_EQUALS, "="), Map.entry(KeyEvent.VK_MINUS, "-"), Map.entry(KeyEvent.VK_SLASH, "/"),
            Map.entry(KeyEvent.VK_BACK_SLASH, "\\"), Map.entry(KeyEvent.VK_BACK_QUOTE, "`"));

    public static String describe(KeyStroke ks) {
        String mods = InputEvent.getModifiersExText(ks.getModifiers());
        String key = PUNCTUATION.getOrDefault(ks.getKeyCode(), KeyEvent.getKeyText(ks.getKeyCode()));
        return mods.isEmpty() ? key : mods + "+" + key;
    }

    public static String describe(List<KeyStroke> keys) {
        StringJoiner j = new StringJoiner(", ");
        for (KeyStroke k : keys) j.add(describe(k));
        return j.toString();
    }

    /** Every action's keys, defaults included, in definition order. */
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Def d : defs.values()) {
            List<Object> keys = new ArrayList<>();
            for (KeyStroke ks : bindings(d.id())) keys.add(name(ks));
            m.put(d.id(), keys);
        }
        return m;
    }

    /**
     * Applies bindings read from settings. Actions missing from the map keep their defaults.
     *
     * @return descriptions of entries that could not be used
     */
    public List<String> fromJson(Map<String, Object> m) {
        List<String> problems = new ArrayList<>();
        Map<String, List<KeyStroke>> next = new HashMap<>();
        for (var e : m.entrySet()) {
            if (!defs.containsKey(e.getKey())) {
                problems.add(L10n.t("keys.error.action", "name", e.getKey()));
                continue;
            }
            if (!(e.getValue() instanceof List<?> list)) {
                problems.add(L10n.t("keys.error.list", "name", e.getKey()));
                continue;
            }
            List<KeyStroke> keys = new ArrayList<>();
            for (Object o : list) {
                KeyStroke ks = o instanceof String str ? parse(str) : null;
                if (ks == null) problems.add(L10n.t("keys.error.key", "key", o, "name", e.getKey()));
                else keys.add(ks);
            }
            next.put(e.getKey(), keys);
        }
        setBindings(next);
        return problems;
    }

    private static final Map<Integer, String> KEY_NAMES = new HashMap<>();
    private static final Map<String, Integer> KEY_CODES = new HashMap<>();

    static {
        for (var f : KeyEvent.class.getFields()) {
            if (!f.getName().startsWith("VK_") || f.getType() != int.class) continue;
            try {
                int code = f.getInt(null);
                String n = f.getName().substring(3);
                KEY_NAMES.putIfAbsent(code, n);
                KEY_CODES.put(n, code);
            } catch (IllegalAccessException ignored) {
                // public static fields are always readable
            }
        }
        String[][] chars = {{",", "COMMA"}, {".", "PERIOD"}, {";", "SEMICOLON"}, {"'", "QUOTE"}, {"[", "OPEN_BRACKET"},
                {"]", "CLOSE_BRACKET"}, {"=", "EQUALS"}, {"-", "MINUS"}, {"/", "SLASH"}, {"\\", "BACK_SLASH"},
                {"`", "BACK_QUOTE"}, {"ESC", "ESCAPE"}, {"DEL", "DELETE"}, {"RETURN", "ENTER"}};
        for (String[] c : chars) KEY_CODES.put(c[0], KEY_CODES.get(c[1]));
    }

    /** Writes a key as text, for example {@code ctrl+shift+Z} or {@code OPEN_BRACKET}. */
    public static String name(KeyStroke ks) {
        int m = ks.getModifiers();
        StringBuilder sb = new StringBuilder();
        if ((m & InputEvent.CTRL_DOWN_MASK) != 0) sb.append("ctrl+");
        if ((m & InputEvent.META_DOWN_MASK) != 0) sb.append("meta+");
        if ((m & InputEvent.ALT_DOWN_MASK) != 0) sb.append("alt+");
        if ((m & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) sb.append("altgraph+");
        if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) sb.append("shift+");
        return sb.append(KEY_NAMES.getOrDefault(ks.getKeyCode(), String.valueOf(ks.getKeyCode()))).toString();
    }

    /** Reads a key written by {@link #name}. Also accepts single characters such as "," and "[". Returns null if unknown. */
    public static KeyStroke parse(String text) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        // The key itself may be "+" or "-", so split off modifiers from the left only.
        int mods = 0;
        while (true) {
            int plus = t.indexOf('+', 1);
            if (plus < 0) break;
            String mod = t.substring(0, plus).trim().toLowerCase(Locale.ROOT);
            int mask = switch (mod) {
                case "ctrl", "control" -> InputEvent.CTRL_DOWN_MASK;
                case "shift" -> InputEvent.SHIFT_DOWN_MASK;
                case "alt" -> InputEvent.ALT_DOWN_MASK;
                case "meta", "cmd", "command" -> InputEvent.META_DOWN_MASK;
                case "altgraph", "altgr" -> InputEvent.ALT_GRAPH_DOWN_MASK;
                default -> -1;
            };
            if (mask < 0) return null;
            mods |= mask;
            t = t.substring(plus + 1).trim();
        }
        Integer code = KEY_CODES.get(t);
        if (code == null) code = KEY_CODES.get(t.toUpperCase(Locale.ROOT));
        return code == null ? null : KeyStroke.getKeyStroke(code, mods);
    }
}
