package compozart.text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static compozart.test.Check.*;

/** Checks the source against en.json: keys exist, keys are used, and values never borrow a term's name. */
public class CoverageTest {
    static final Path SRC = Path.of("src/main/java");
    static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    static Map<Path, String> sources() throws Exception {
        Map<Path, String> out = new TreeMap<>();
        try (Stream<Path> s = Files.walk(SRC)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) out.put(p, Files.readString(p));
        }
        return out;
    }

    /** The argument list of every L10n.t(...) and L10n.plural(...) call, split at top-level commas. */
    static List<List<String>> calls(String src) {
        List<List<String>> out = new ArrayList<>();
        Matcher m = Pattern.compile("L10n\\.(t|plural)\\(").matcher(src);
        while (m.find()) {
            List<String> args = new ArrayList<>();
            int depth = 0, start = m.end();
            boolean inString = false;
            for (int i = m.end(); i < src.length(); i++) {
                char c = src.charAt(i);
                if (inString) {
                    if (c == '\\') i++;
                    else if (c == '"') inString = false;
                } else if (c == '"') inString = true;
                else if (c == '(') depth++;
                else if (c == ')' && depth-- == 0) {
                    args.add(src.substring(start, i).trim());
                    break;
                } else if (c == ',' && depth == 0) {
                    args.add(src.substring(start, i).trim());
                    start = i + 1;
                }
            }
            if (m.group(1).equals("plural") && args.size() > 1) args.remove(1); // the count
            out.add(args);
        }
        return out;
    }

    public void testEveryLookedUpKeyExists() throws Exception {
        Set<String> missing = new TreeSet<>();
        for (String src : sources().values()) {
            for (List<String> args : calls(src)) {
                Matcher lit = LITERAL.matcher(args.get(0));
                if (lit.matches() && !L10n.englishKeys().contains(lit.group(1))) missing.add(lit.group(1));
            }
        }
        eq(Set.of(), missing);
    }

    public void testEveryEnglishKeyIsUsed() throws Exception {
        Set<String> literals = new HashSet<>();
        for (String src : sources().values()) {
            Matcher m = LITERAL.matcher(src);
            while (m.find()) literals.add(m.group(1));
        }
        Set<String> unused = new TreeSet<>();
        for (String key : L10n.englishKeys()) {
            // "New node in {path}" variants are looked up as base key + ".in"
            String base = key.endsWith(".in") ? key.substring(0, key.length() - 3) : key;
            if (!literals.contains(key) && !literals.contains(base)) unused.add(key);
        }
        eq(Set.of(), unused);
    }

    public void testValueNamesNeverShadowTerms() throws Exception {
        Set<String> clashes = new TreeSet<>();
        for (var e : sources().entrySet()) {
            for (List<String> args : calls(e.getValue())) {
                for (int i = 1; i < args.size(); i += 2) {
                    Matcher lit = LITERAL.matcher(args.get(i));
                    if (lit.matches() && L10n.englishTerms().contains(lit.group(1))) {
                        clashes.add(e.getKey().getFileName() + ": " + args.get(0) + " passes \"" + lit.group(1) + "\"");
                    }
                }
            }
        }
        eq(Set.of(), clashes);
    }

    public void testEnglishPlaceholdersAreTermsOrPassedValues() throws Exception {
        // every value placeholder in English must be passed by at least one call of that key
        Map<String, Set<String>> passed = new HashMap<>();
        for (String src : sources().values()) {
            for (List<String> args : calls(src)) {
                Matcher key = LITERAL.matcher(args.get(0));
                if (!key.matches()) continue;
                Set<String> names = passed.computeIfAbsent(key.group(1), k -> new HashSet<>());
                for (int i = 1; i < args.size(); i += 2) {
                    Matcher lit = LITERAL.matcher(args.get(i));
                    if (lit.matches()) names.add(lit.group(1));
                }
                names.add("n");
            }
        }
        Set<String> problems = new TreeSet<>();
        for (var e : passed.entrySet()) {
            Object english = L10n.englishRaw(e.getKey());
            if (english == null) continue;
            for (String name : L10n.valuePlaceholders(english)) {
                if (!e.getValue().contains(name)) problems.add(e.getKey() + " needs {" + name + "}");
            }
        }
        eq(Set.of(), problems);
    }
}
