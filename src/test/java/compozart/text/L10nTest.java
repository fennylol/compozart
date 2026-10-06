package compozart.text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static compozart.test.Check.*;

public class L10nTest {
    static Path dirWith(String code, String json) throws Exception {
        Path dir = Files.createTempDirectory("lang");
        Files.writeString(dir.resolve(code + ".json"), json);
        return dir;
    }

    static void english() {
        L10n.use(null, L10n.ENGLISH);
    }

    public void testFormatAndTerms() {
        english();
        eq("Delete anchor", L10n.t("anchor.delete"));
        eq("Anchors target “a”. Point them at “b” too?", L10n.t("node.rename.retarget", "old", "a", "new", "b"));
        eq("Named anchor 2 of 3", L10n.t("anchor.title.named", "index", 2, "count", 3));
        eq("{x}", L10n.format("{x}"));          // unknown placeholders stay visible
        eq("{literal}", L10n.format("{{literal}}"));
        eq("[no.such.key]", L10n.t("no.such.key"));
    }

    public void testValuesWinOverTerms() {
        english();
        eq("node / here", L10n.format("{node} / {where}", "where", "here"));
        eq("x / here", L10n.format("{node} / {where}", "node", "x", "where", "here"));
    }

    public void testPlurals() {
        english();
        eq("1 color", L10n.plural("export.colors", 1));
        eq("3 colors", L10n.plural("export.colors", 3));
        eq("0 colors", L10n.plural("export.colors", 0));
        eq("one", Plurals.category("fr", 0));
        eq("one", Plurals.category("ru", 21));
        eq("few", Plurals.category("ru", 23));
        eq("many", Plurals.category("ru", 25));
        eq("many", Plurals.category("ru", 12));
        eq("few", Plurals.category("pl", 22));
        eq("many", Plurals.category("pl", 5));
        eq("few", Plurals.category("cs", 3));
        eq("other", Plurals.category("ja", 1));
        eq("other", Plurals.category("en", 2));
    }

    public void testTranslationWithFormsAndFallback() throws Exception {
        Path dir = dirWith("xx", """
                {"language": "Testisch", "code": "xx",
                 "terms": {"anchor": {"one": "Anker", "other": "Anker", "dative.other": "Ankern"}},
                 "strings": {
                   "anchor.delete": "{Anchor} löschen",
                   "help.eraser.anchors": "Rechtsklick löscht {anchor:other}, auch mit {anchor:dative.other}.",
                   "export.colors": {"one": "{n} Farbe", "other": "{n} Farben"}
                 }}""");
        try {
            eq(null, L10n.use(dir, "xx"));
            eq("xx", L10n.code());
            eq("Anker löschen", L10n.t("anchor.delete"));
            eq("Rechtsklick löscht Anker, auch mit Ankern.", L10n.t("help.eraser.anchors"));
            eq("2 Farben", L10n.plural("export.colors", 2));
            eq("Undo", L10n.t("action.edit.undo"));           // missing here: falls back to English
            eq("Delete node", L10n.t("action.node.delete"));  // English text, English term
            List<L10n.Option> options = L10n.available(dir);
            yes(options.contains(new L10n.Option("xx", "Testisch")), "listed by name");
        } finally {
            english();
        }
    }

    public void testMissingPlaceholdersAreReported() {
        List<String> problems = L10n.check("""
                {"strings": {"dialog.open.error": "Konnte nicht öffnen", "status.saved": "Gespeichert: {file}",
                 "anchor.delete": "Anker löschen"}}""");
        eq(1, problems.size());
        yes(problems.get(0).contains("dialog.open.error") && problems.get(0).contains("{file}"), problems.get(0));
    }

    public void testPseudoBrackets() {
        try {
            L10n.use(null, L10n.PSEUDO);
            eq("[Delete anchor]", L10n.t("anchor.delete"));
            eq("[3 colors]", L10n.plural("export.colors", 3));
        } finally {
            english();
        }
    }

    public void testUnknownLanguageFallsBack() {
        try {
            String problem = L10n.use(null, "zz");
            yes(problem != null && problem.contains("zz.json"), "reported");
            eq("Delete anchor", L10n.t("anchor.delete"));
        } finally {
            english();
        }
    }

    public void testBundledGerman() {
        try {
            eq(List.of("de"), L10n.bundledCodes());
            eq(null, L10n.use(null, "de"));
            eq("Anker löschen", L10n.t("anchor.delete"));
            eq("Der erste Anker eines Knotens wird sein Wurzelanker. Umschalt+Ziehen setzt oder verschiebt den Wurzelanker.",
                    L10n.t("help.anchor.first"));
            eq("3 Farben", L10n.plural("export.colors", 3));
            eq("Bei 4096 Teilen angehalten.", L10n.t("compose.warning.truncated", "count", 4096));
            yes(L10n.available(null).contains(new L10n.Option("de", "Deutsch")), "listed");
        } finally {
            english();
        }
    }

    public void testBundledTranslationsAreComplete() {
        for (String code : L10n.bundledCodes()) {
            String text = L10n.bundledText(code);
            yes(text != null, code + ".json is bundled");
            eq(code + " placeholders", List.of(), L10n.check(text));
            java.util.Map<String, Object> strings = compozart.io.Json.obj(
                    compozart.io.Json.obj(compozart.io.Json.parse(text), code).get("strings"), "strings");
            java.util.Set<String> missing = new java.util.TreeSet<>(L10n.englishKeys());
            missing.removeAll(strings.keySet());
            eq(code + " missing keys", java.util.Set.of(), missing);
            java.util.Set<String> extra = new java.util.TreeSet<>(strings.keySet());
            extra.removeAll(L10n.englishKeys());
            eq(code + " unknown keys", java.util.Set.of(), extra);
        }
    }
}
