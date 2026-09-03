package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;

/**
 * Outer-shell ViewModel for the simulation workbench (step navigation, log, MMU/TLB/OS tab hosting).
 */
public class SimulationViewModel 
{
    private final Simulation simulation;
    private final SimulationContext context;

    private final IntegerProperty currentStepNumber = new SimpleIntegerProperty(0);
    private final StringProperty stepDescription = 
            new SimpleStringProperty("Simulation ready. Press \"Next\" to begin stepping through execution.");
    private final ObservableList<String> logEntries = FXCollections.observableArrayList();

    private final StringProperty currentVirtualAddressHex = new SimpleStringProperty("/");
    private final StringProperty currentPhysicalAddressHex = new SimpleStringProperty("/");

    public SimulationViewModel(Simulation simulation) 
    {
        this.simulation = simulation;
        this.context = simulation.getContext();
    }

    public IntegerProperty currentStepNumberProperty() 
    {
        return currentStepNumber;
    }

    public StringProperty stepDescriptionProperty() 
    {
        return stepDescription;
    }

    public ObservableList<String> getLogEntries() 
    {
        return logEntries;
    }

    public StringProperty currentVirtualAddressHexProperty() 
    {
        return currentVirtualAddressHex;
    }

    public StringProperty currentPhysicalAddressHexProperty() 
    {
        return currentPhysicalAddressHex;
    }

    public SimulationContext getContext() 
    {
        return context;
    }

    public Simulation getSimulation()
    {
        return simulation;
    }

    public void executeNextStep() 
    {
        try {
            simulation.nextStep();
            currentStepNumber.set(simulation.getCurrentStepNum());
            stepDescription.set(simulation.getLastStepDescription());
            logEntries.add("Step " + currentStepNumber.get() + ": " + simulation.getLastStepDescription());
        } catch (Exception e) {
            stepDescription.set("Step engine is not yet wired up for this configuration.");
            logEntries.add("Next step failed: " + e.getMessage());
        }
        refreshAddressDisplays();
    }

    public void executePreviousStep() 
    {
        try {
            simulation.previousStep();
            currentStepNumber.set(simulation.getCurrentStepNum());
            stepDescription.set(simulation.getLastStepDescription());
            logEntries.add("Step " + (currentStepNumber.get() + 1) + " undone: " + simulation.getLastStepDescription());
        } catch (Exception e) {
            stepDescription.set("No earlier step to revert to.");
            logEntries.add("Previous step failed: " + e.getMessage());
        }
        refreshAddressDisplays();
    }

    private void refreshAddressDisplays() 
    {
        if (!context.hasCurrentInstruction()) {
            currentVirtualAddressHex.set("/");
            currentPhysicalAddressHex.set("/");
            return;
        }

        currentVirtualAddressHex.set(String.format("0x%X", context.getCurrentInstruction().getVirtualAddress()));

        long physicalAddress = context.getCurrentPhysicalAddress();
        currentPhysicalAddressHex.set(physicalAddress < 0 ? "/" : String.format("0x%X", physicalAddress));
    }
}
