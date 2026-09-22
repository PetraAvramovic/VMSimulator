package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import rs.ac.bg.etf.viewmodel.listeners.MainMenuNavigationListener;

public class MainMenuViewModel
{
    private final MainMenuNavigationListener navigationListener;

    // True once a simulation has been launched at least once, so the menu can offer "Resume".
    private final BooleanProperty resumeAvailable = new SimpleBooleanProperty(false);

    public MainMenuViewModel(MainMenuNavigationListener navigationListener)
    {
        this.navigationListener = navigationListener;
    }

    public BooleanProperty resumeAvailableProperty()
    {
        return resumeAvailable;
    }

    public void executeStartNavigation()
    {
        navigationListener.onMainMenuToStart();
    }

    public void executeResumeNavigation()
    {
        navigationListener.onMainMenuToResume();
    }

    public void executeSettingsRequest()
    {
        navigationListener.onMainMenuToSettings();
    }
}
