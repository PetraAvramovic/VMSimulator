package rs.ac.bg.etf.viewmodel.listeners;

import rs.ac.bg.etf.model.simulation.SimulationConfig;

public interface ConfigurationNavigationListener 
{
    void onConfigToSimulation(SimulationConfig finalizedConfig);
    void onConfigToMainMenu();
}