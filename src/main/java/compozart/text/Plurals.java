package compozart.text;

import java.util.Set;

/**
 * Plural categories (Unicode CLDR names: zero, one, two, few, many, other) for the common language families.
 * A language file only needs the categories its language uses; missing ones fall back to "other".
 */
final class Plurals {
    private static final Set<String> NO_PLURALS = Set.of("ja", "zh", "ko", "vi", "th", "id", "ms");
    private static final Set<String> ZERO_IS_ONE = Set.of("fr", "pt");
    private static final Set<String> EAST_SLAVIC = Set.of("ru", "uk", "be");
    private static final Set<String> WEST_SLAVIC_CZ = Set.of("cs", "sk");

    private Plurals() {
    }

    static String category(String code, long n) {
        String lang = code.split("[-_]")[0].toLowerCase(java.util.Locale.ROOT);
        long a = Math.abs(n);
        long mod10 = a % 10, mod100 = a % 100;
        if (NO_PLURALS.contains(lang)) return "other";
        if (ZERO_IS_ONE.contains(lang)) return a <= 1 ? "one" : "other";
        if (EAST_SLAVIC.contains(lang)) {
            if (mod10 == 1 && mod100 != 11) return "one";
            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "few";
            return "many";
        }
        if (lang.equals("pl")) {
            if (a == 1) return "one";
            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "few";
            return "many";
        }
        if (WEST_SLAVIC_CZ.contains(lang)) {
            if (a == 1) return "one";
            if (a >= 2 && a <= 4) return "few";
            return "other";
        }
        return a == 1 ? "one" : "other";
    }
}
