package rs.ac.bg.etf.viewmodel.listeners;

public interface SimulationNavigationListener
{
    void onSimulationToMainMenu();

    /** The user asked to start a new simulation from within the workbench (not the main menu). */
    void onSimulationToNewSimulation();
}
