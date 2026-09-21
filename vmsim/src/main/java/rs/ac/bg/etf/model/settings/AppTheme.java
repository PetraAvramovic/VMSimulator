package rs.ac.bg.etf.model.settings;

import java.util.Locale;

/**
 * The colour themes the app can be drawn in. Each constant has a stylesheet named after it
 * ({@code light-theme.css}, {@code dark-theme.css}); every theme's stylesheet defines the same style
 * classes at the same sizes, so switching only ever changes colours.
 */
public enum AppTheme {
    LIGHT("Light"),
    DARK("Dark");

    private static final String STYLESHEET_SUFFIX = "-theme.css";

    private final String displayName;

    AppTheme(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** File name of this theme's stylesheet, next to the other view resources. */
    public String stylesheetName() {
        return name().toLowerCase(Locale.ROOT) + STYLESHEET_SUFFIX;
    }
}
