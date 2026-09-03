package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationConfig;
import rs.ac.bg.etf.model.simulation.SimulationFactory;
import rs.ac.bg.etf.viewmodel.listeners.ConfigurationNavigationListener;
import rs.ac.bg.etf.viewmodel.listeners.MainMenuNavigationListener;

public class AppViewModel implements MainMenuNavigationListener, ConfigurationNavigationListener
{
    private final MainMenuViewModel mainMenuViewModel;
    private final ConfigurationViewModel configurationViewModel;
    private SimulationViewModel simulationViewModel;
    
    private ObjectProperty<ApplicationScreenState> currentScreen = new SimpleObjectProperty<>();

    public AppViewModel() 
    {
        this.mainMenuViewModel = new MainMenuViewModel(this);
        this.configurationViewModel = new ConfigurationViewModel(this);
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
        System.out.println("AppViewModel: Caught settings signal from the main menu.");
    }

    @Override
    public void onConfigToSimulation(SimulationConfig finalizedConfig) 
    {
        try {
            Simulation simulation = SimulationFactory.createSimulation(finalizedConfig);
            simulation.init();

            this.simulationViewModel = new SimulationViewModel(simulation);
            this.currentScreen.set(ApplicationScreenState.SIMULATION);
        } catch (Exception e) {
            System.out.println("AppViewModel: Failed to initialize simulation - " + e.getMessage());
        }
    }

    @Override
    public void onConfigToMainMenu() 
    {
        this.currentScreen.set(ApplicationScreenState.MAIN_MENU);
    }
}
