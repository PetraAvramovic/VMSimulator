package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import rs.ac.bg.etf.model.settings.AppSettings;
import rs.ac.bg.etf.model.settings.AppTheme;
import rs.ac.bg.etf.viewmodel.listeners.SettingsNavigationListener;

public class SettingsViewModel
{
    private final SettingsNavigationListener navigationListener;

    // The theme the app is drawn in. Starts as the saved one and is saved again whenever it changes;
    // App listens to it to restyle every open window.
    private final ObjectProperty<AppTheme> theme = new SimpleObjectProperty<>();

    public SettingsViewModel(AppSettings settings, SettingsNavigationListener navigationListener)
    {
        this.navigationListener = navigationListener;

        theme.set(settings.loadTheme());
        // A ComboBox bound to this property can momentarily clear it; there is nothing to save or show then.
        theme.addListener((observable, oldTheme, newTheme) -> {
            if (newTheme != null)
                settings.saveTheme(newTheme);
        });
    }

    public ObjectProperty<AppTheme> themeProperty()
    {
        return theme;
    }

    public void executeBackNavigation()
    {
        navigationListener.onSettingsToMainMenu();
    }
}
