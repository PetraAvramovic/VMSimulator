package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import rs.ac.bg.etf.model.settings.AppSettings;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationConfig;
import rs.ac.bg.etf.model.simulation.SimulationFactory;
import rs.ac.bg.etf.viewmodel.listeners.ConfigurationNavigationListener;
import rs.ac.bg.etf.viewmodel.listeners.MainMenuNavigationListener;
import rs.ac.bg.etf.viewmodel.listeners.SettingsNavigationListener;
import rs.ac.bg.etf.viewmodel.listeners.SimulationNavigationListener;

public class AppViewModel implements MainMenuNavigationListener, ConfigurationNavigationListener, SimulationNavigationListener, SettingsNavigationListener
{
    private final MainMenuViewModel mainMenuViewModel;
    private final ConfigurationViewModel configurationViewModel;
    private final SettingsViewModel settingsViewModel;
    private SimulationViewModel simulationViewModel;

    private ObjectProperty<ApplicationScreenState> currentScreen = new SimpleObjectProperty<>();

    public AppViewModel()
    {
        this.mainMenuViewModel = new MainMenuViewModel(this);
        this.configurationViewModel = new ConfigurationViewModel(this);
        this.settingsViewModel = new SettingsViewModel(new AppSettings(), this);
    }

    public ObjectProperty<ApplicationScreenState> currentScreenProperty()
    { 
        return currentScreen; 
    }

    public MainMenuViewModel getMainMenuViewModel() 
    {
        return mainMenuViewModel;
    }

    public ConfigurationViewModel getConfigurationViewModel()
    {
        return configurationViewModel;
    }

    public SettingsViewModel getSettingsViewModel()
    {
        return settingsViewModel;
    }

    public SimulationViewModel getSimulationViewModel()
    {
        return simulationViewModel;
    }

    @Override
    public void onMainMenuToStart()
    {
        this.currentScreen.set(ApplicationScreenState.CONFIGURATION);
    }

    @Override
    public void onMainMenuToSettings()
    {
        this.currentScreen.set(ApplicationScreenState.SETTINGS);
    }

    @Override
    public void onSettingsToMainMenu()
    {
        this.currentScreen.set(ApplicationScreenState.MAIN_MENU);
    }

    @Override
    public void onConfigToSimulation(SimulationConfig finalizedConfig) 
    {
        try {
            Simulation simulation = SimulationFactory.createSimulation(finalizedConfig);
            simulation.init();

            this.simulationViewModel = new SimulationViewModel(simulation, this);
            this.mainMenuViewModel.resumeAvailableProperty().set(true);
            this.currentScreen.set(ApplicationScreenState.SIMULATION);
        } catch (Exception e) {
            // A bad config should have already been rejected on the configuration screen; if one
            // still gets here, staying on that screen (currentScreen untouched) beats navigating to
            // a broken simulation.
        }
    }

    @Override
    public void onConfigToMainMenu()
    {
        this.currentScreen.set(ApplicationScreenState.MAIN_MENU);
    }

    @Override
    public void onSimulationToMainMenu()
    {
        this.currentScreen.set(ApplicationScreenState.MAIN_MENU);
    }

    @Override
    public void onSimulationToNewSimulation()
    {
        // The old simulationViewModel is simply overwritten once onConfigToSimulation runs; nothing
        // to tear down here (mirrors how the main menu's own "Start" already gets here).
        this.currentScreen.set(ApplicationScreenState.CONFIGURATION);
    }

    @Override
    public void onMainMenuToResume()
    {
        if (simulationViewModel != null) {
            this.currentScreen.set(ApplicationScreenState.SIMULATION);
        }
    }
}
