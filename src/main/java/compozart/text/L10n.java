package compozart.text;

import compozart.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Every user-visible string, looked up by key from a language file.
 *
 * <p>A language file is JSON with a display name, a {@code terms} table and a {@code strings} table:
 * <pre>
 * {
 *   "language": "English",
 *   "code": "en",
 *   "terms": { "anchor": { "one": "anchor", "other": "anchors" } },
 *   "strings": {
 *     "help.anchors": "{Anchor:other} define how {node:other} connect to each other.",
 *     "palette.count": { "one": "{n} color", "other": "{n} colors" }
 *   }
 * }
 * </pre>
 *
 * <p>Placeholders are {@code {name}} for values the code passes in, or {@code {term:form}} for a term.
 * A term placeholder that starts with a capital letter capitalizes the word. A missing form falls back to
 * {@code other}, then {@code one}. A value passed in by the code wins over a term with the same name.
 * {@code {{} and {@code }}} write literal braces.
 *
 * <p>English ships inside the jar and fills any gap in another language. A key missing everywhere shows as
 * {@code [key]} so it is easy to spot.
 */
public final class L10n {
    public static final String ENGLISH = "en";
    /** A testing language that brackets every string, so text that skipped localization stands out. */
    public static final String PSEUDO = "pseudo";

    /** A language that can be chosen. */
    public record Option(String code, String name) {
    }

    private record Lang(String code, String name, Map<String, Object> strings, Map<String, Map<String, String>> terms) {
    }

    private static final Lang BUILT_IN = parse(builtInText(), ENGLISH, new ArrayList<>());
    private static volatile Lang current = BUILT_IN;
    private static volatile boolean pseudo;

    private L10n() {
    }

    // ---- lookup ----

    /**
     * The string for {@code key}, with placeholders filled from name/value pairs:
     * {@code t("library.rename.retarget", "old", a, "new", b)}.
     */
    public static String t(String key, Object... args) {
        Object v = lookup(key);
        if (v instanceof Map<?, ?> forms) v = pick(forms, "other");
        if (v == null) return "[" + key + "]";
        return finish(format(String.valueOf(v), args));
    }

    /** A plural string: the form for {@code n} in the current language, with {@code n} also passed as {n}. */
    public static String plural(String key, long n, Object... args) {
        Object v = lookup(key);
        String text;
        if (v instanceof Map<?, ?> forms) text = pick(forms, Plurals.category(current.code, n));
        else if (v != null) text = String.valueOf(v);
        else return "[" + key + "]";
        Object[] all = Arrays.copyOf(args, args.length + 2);
        all[args.length] = "n";
        all[args.length + 1] = n;
        return finish(format(text, all));
    }

    /** A term on its own, such as the word for "node", in the given form. */
    public static String term(String name, String form) {
        return finish(termForm(name, form));
    }

    private static Object lookup(String key) {
        Object v = current.strings.get(key);
        return v != null ? v : BUILT_IN.strings.get(key);
    }

    private static String pick(Map<?, ?> forms, String category) {
        Object v = forms.get(category);
        if (v == null) v = forms.get("other");
        if (v == null) v = forms.get("one");
        if (v == null && !forms.isEmpty()) v = forms.values().iterator().next();
        return v == null ? "" : String.valueOf(v);
    }

    private static String finish(String s) {
        return pseudo ? "[" + s + "]" : s;
    }

    static String format(String template, Object... args) {
        StringBuilder out = new StringBuilder(template.length() + 16);
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                out.append('{');
                i++;
            } else if (c == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                out.append('}');
                i++;
            } else if (c == '{') {
                int end = template.indexOf('}', i);
                if (end < 0) {
                    out.append(template, i, template.length());
                    break;
                }
                out.append(resolve(template.substring(i + 1, end), args));
                i = end;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String resolve(String placeholder, Object[] args) {
        int colon = placeholder.indexOf(':');
        String name = colon < 0 ? placeholder : placeholder.substring(0, colon);
        String form = colon < 0 ? null : placeholder.substring(colon + 1);
        for (int k = 0; k + 1 < args.length; k += 2) {
            if (name.equals(args[k])) return String.valueOf(args[k + 1]);
        }
        String lower = name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (hasTerm(lower)) {
            String word = termForm(lower, form == null ? "one" : form);
            boolean capital = !name.isEmpty() && Character.isUpperCase(name.charAt(0));
            return capital && !word.isEmpty() ? word.substring(0, 1).toUpperCase(locale()) + word.substring(1) : word;
        }
        return "{" + placeholder + "}";
    }

    private static boolean hasTerm(String name) {
        return current.terms.containsKey(name) || BUILT_IN.terms.containsKey(name);
    }

    private static String termForm(String name, String form) {
        Map<String, String> forms = current.terms.get(name);
        if (forms == null) forms = BUILT_IN.terms.get(name);
        if (forms == null) return "[" + name + "]";
        String v = forms.get(form);
        if (v == null) v = forms.get("other");
        if (v == null) v = forms.get("one");
        return v == null ? "" : v;
    }

    private static Locale locale() {
        return Locale.forLanguageTag(current.code);
    }

    // ---- languages ----

    /** The current language's code, such as "en" or "de". */
    public static String code() {
        return pseudo ? PSEUDO : current.code;
    }

    /**
     * Switches language. {@code choice} is a code, "auto" for the system language, or {@link #PSEUDO}.
     * Translations live in {@code dir} as {@code <code>.json}. Also sets Java's default locale so Swing's
     * own buttons (OK, Cancel) follow.
     *
     * @return problems worth showing, or null
     */
    public static String use(Path dir, String choice) {
        List<String> problems = new ArrayList<>();
        pseudo = PSEUDO.equals(choice);
        String code = choice == null || choice.isBlank() || "auto".equals(choice)
                ? Locale.getDefault().getLanguage() : choice;
        Lang chosen = BUILT_IN;
        if (!pseudo && !code.equals(ENGLISH)) {
            Path f = dir == null ? null : dir.resolve(code + ".json");
            Lang bundled = bundled(code);
            if (f != null && Files.isRegularFile(f)) {
                // a file in the folder adjusts a bundled translation, or is a whole new one
                Lang file = load(f, code, problems);
                chosen = bundled != null ? merge(bundled, file) : file;
                problems.addAll(missingPlaceholders(file));
            } else if (bundled != null) {
                chosen = bundled;
            } else if (!"auto".equals(choice)) {
                problems.add("no language file " + code + ".json");
            }
        } else if (code.equals(ENGLISH) && dir != null && Files.isRegularFile(dir.resolve("en.json"))) {
            // a customized English file adjusts the built-in wording
            chosen = merge(BUILT_IN, load(dir.resolve("en.json"), ENGLISH, problems));
        }
        current = chosen;
        Locale.setDefault(locale());
        javax.swing.JComponent.setDefaultLocale(locale());
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /** English, the bundled translations, every language file in {@code dir}, and the pseudo language, by display name. */
    public static List<Option> available(Path dir) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(ENGLISH, BUILT_IN.name));
        Set<String> listed = new HashSet<>(Set.of(ENGLISH));
        for (String code : bundledCodes()) {
            Lang l = bundled(code);
            if (l != null && listed.add(code)) out.add(new Option(code, l.name));
        }
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path f : s.sorted().toList()) {
                    String n = f.getFileName().toString();
                    if (!n.endsWith(".json") || n.endsWith(".template.json") || n.equals("en.json")) continue;
                    String code = n.substring(0, n.length() - 5);
                    if (!listed.add(code)) continue;
                    Lang l = load(f, code, new ArrayList<>());
                    out.add(new Option(code, l.name));
                }
            } catch (IOException ignored) {
                // an unreadable folder just offers English
            }
        }
        out.add(new Option(PSEUDO, "Pseudo (testing)"));
        return out;
    }

    /**
     * Writes {@code en.template.json}, the full English file, for translators to copy. It is rewritten on
     * every launch so it always lists the current keys; translations go in their own files.
     */
    public static void writeTemplate(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("en.template.json"), builtInText(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // a read-only install simply has no template
        }
    }

    /**
     * Strings in a translation that leave out a value placeholder their English text has, such as {file}.
     * Terms are not checked: a translation may write the word itself instead.
     */
    private static List<String> missingPlaceholders(Lang lang) {
        List<String> out = new ArrayList<>();
        for (var e : lang.strings.entrySet()) {
            Object english = BUILT_IN.strings.get(e.getKey());
            if (english == null) continue;
            Set<String> need = valuePlaceholders(english);
            need.removeAll(valuePlaceholders(e.getValue()));
            need.remove("n"); // a plural form may spell the number out
            if (!need.isEmpty()) out.add(e.getKey() + " is missing {" + String.join("}, {", need) + "}");
        }
        Collections.sort(out);
        return out;
    }

    /** The value placeholders (not terms) in a string or in every form of a plural. */
    static Set<String> valuePlaceholders(Object text) {
        Set<String> out = new TreeSet<>();
        if (text instanceof Map<?, ?> forms) {
            for (Object f : forms.values()) out.addAll(valuePlaceholders(f));
            return out;
        }
        String s = String.valueOf(text).replace("{{", "").replace("}}", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}:]+)(?::[^}]*)?}").matcher(s);
        while (m.find()) {
            String name = m.group(1);
            String lower = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            if (!BUILT_IN.terms.containsKey(lower)) out.add(name);
        }
        return out;
    }

    /** Checks a translation's text against English, for tests and tools. Returns the problems found. */
    public static List<String> check(String translationJson) {
        List<String> problems = new ArrayList<>();
        Lang l = parse(translationJson, "xx", problems);
        problems.addAll(missingPlaceholders(l));
        return problems;
    }

    /** Every key in the built-in English file, for tests. */
    public static Set<String> englishKeys() {
        return Collections.unmodifiableSet(BUILT_IN.strings.keySet());
    }

    /** Every term in the built-in English file, for tests. */
    public static Set<String> englishTerms() {
        return Collections.unmodifiableSet(BUILT_IN.terms.keySet());
    }

    /** The raw English template for a key, for tests. */
    public static Object englishRaw(String key) {
        return BUILT_IN.strings.get(key);
    }

    /** Codes of the translations shipped inside the jar, from languages/index.txt. */
    public static List<String> bundledCodes() {
        try (InputStream in = L10n.class.getResourceAsStream("/compozart/defaults/languages/index.txt")) {
            if (in == null) return List.of();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static final Map<String, Lang> BUNDLED = new HashMap<>();

    /** A translation shipped inside the jar, or null. */
    private static synchronized Lang bundled(String code) {
        if (!bundledCodes().contains(code)) return null;
        return BUNDLED.computeIfAbsent(code, c -> {
            try (InputStream in = L10n.class.getResourceAsStream("/compozart/defaults/languages/" + c + ".json")) {
                if (in == null) return null;
                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), c, new ArrayList<>());
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** The raw text of a bundled translation, for tests. */
    public static String bundledText(String code) {
        try (InputStream in = L10n.class.getResourceAsStream("/compozart/defaults/languages/" + code + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static String builtInText() {
        try (InputStream in = L10n.class.getResourceAsStream("/compozart/defaults/languages/en.json")) {
            if (in == null) throw new IllegalStateException("missing built-in en.json");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Lang load(Path f, String code, List<String> problems) {
        try {
            return parse(Files.readString(f, StandardCharsets.UTF_8), code, problems);
        } catch (IOException | RuntimeException e) {
            problems.add(f.getFileName() + ": " + e.getMessage());
            return new Lang(code, code, Map.of(), Map.of());
        }
    }

    static Lang parse(String text, String fallbackCode, List<String> problems) {
        Map<String, Object> root = Json.obj(Json.parse(text), "language file");
        String code = Json.str(root, "code", fallbackCode);
        String name = Json.str(root, "language", code);
        Map<String, Map<String, String>> terms = new HashMap<>();
        if (root.get("terms") != null) {
            for (var e : Json.obj(root.get("terms"), "terms").entrySet()) {
                Map<String, String> forms = new LinkedHashMap<>();
                if (e.getValue() instanceof String s) forms.put("one", s);
                else for (var f : Json.obj(e.getValue(), "term " + e.getKey()).entrySet()) forms.put(f.getKey(), String.valueOf(f.getValue()));
                terms.put(e.getKey(), forms);
            }
        }
        Map<String, Object> strings = new HashMap<>();
        if (root.get("strings") != null) {
            for (var e : Json.obj(root.get("strings"), "strings").entrySet()) {
                Object v = e.getValue();
                if (v instanceof String || v instanceof Map<?, ?>) strings.put(e.getKey(), v);
                else problems.add("string " + e.getKey() + " should be text or plural forms");
            }
        }
        return new Lang(code, name, strings, terms);
    }

    private static Lang merge(Lang base, Lang over) {
        Map<String, Object> s = new HashMap<>(base.strings);
        s.putAll(over.strings);
        Map<String, Map<String, String>> t = new HashMap<>(base.terms);
        t.putAll(over.terms);
        return new Lang(base.code, base.name, s, t);
    }
}
