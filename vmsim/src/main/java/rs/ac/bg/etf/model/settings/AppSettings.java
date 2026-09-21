package rs.ac.bg.etf.model.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The user's application-wide preferences (as opposed to a simulation's configuration), kept across
 * runs in a small properties file in the user's home directory.
 */
public class AppSettings {
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".vmsimulator", "settings.properties");
    private static final String FILE_COMMENT = "VMSimulator settings";

    private static final String THEME_KEY = "theme";
    private static final AppTheme DEFAULT_THEME = AppTheme.LIGHT;

    /** The saved theme, or the default when nothing valid has been saved (or the file can't be read). */
    public AppTheme loadTheme() {
        try {
            return AppTheme.valueOf(read().getProperty(THEME_KEY, DEFAULT_THEME.name()));
        } catch (IllegalArgumentException e) {
            return DEFAULT_THEME;
        }
    }

    /** Remembers {@code theme}; a file that can't be written just means it is not remembered. */
    public void saveTheme(AppTheme theme) {
        Properties properties = read();
        properties.setProperty(THEME_KEY, theme.name());
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(out, FILE_COMMENT);
            }
        } catch (IOException | SecurityException e) {
            System.err.println("AppSettings: could not save the settings - " + e.getMessage());
        }
    }

    // Read again on every access rather than cached, so saving one setting never drops another that
    // was written by a newer version of the app.
    private static Properties read() {
        Properties properties = new Properties();
        if (Files.isRegularFile(SETTINGS_FILE)) {
            try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                // Unreadable or corrupt: behave as if nothing had been saved.
                properties.clear();
            }
        }
        return properties;
    }
}
