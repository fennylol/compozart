package compozart.ui;

import compozart.text.L10n;

import java.nio.file.Path;

/** Loads language, theme and icons before the first window opens. */
public final class Startup {
    private Startup() {
    }

    /** The folder for language files: settings/languages. */
    static Path languagesDir() {
        return AppHome.settingsDir().resolve("languages");
    }

    /** @return problems worth showing in the status bar, or null */
    public static String prepare() {
        Settings settings = new Settings();
        settings.load(null);
        Path languages = languagesDir();
        L10n.writeTemplate(languages);
        String languageProblems = L10n.use(languages, settings.language);
        String lookProblems = Appearance.get().loadAndApply();
        if (languageProblems == null) return lookProblems;
        return lookProblems == null ? languageProblems : languageProblems + "; " + lookProblems;
    }
}
