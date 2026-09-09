package rs.ac.bg.etf.viewmodel;

import java.util.NoSuchElementException;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.viewmodel.listeners.SimulationNavigationListener;

/**
 * Outer-shell ViewModel for the simulation workbench (step navigation, log, MMU/TLB/OS tab hosting).
 */
public class SimulationViewModel 
{
    private final Simulation simulation;
    private final SimulationContext context;
    private final SimulationNavigationListener navigationListener;

    private final IntegerProperty currentStepNumber = new SimpleIntegerProperty(0);
    private final StringProperty stepDescription = 
            new SimpleStringProperty("Simulation ready. Press \"Next\" to begin stepping through execution.");
    private final ObservableList<String> logEntries = FXCollections.observableArrayList();
    private final ObservableList<String> instructionEntries = FXCollections.observableArrayList();

    private final StringProperty currentVirtualAddressHex = new SimpleStringProperty("/");
    private final StringProperty currentPhysicalAddressHex = new SimpleStringProperty("/");

    /** Row index into {@link #instructionEntries} of the instruction currently being executed, or -1. */
    private final IntegerProperty currentInstructionIndex = new SimpleIntegerProperty(-1);

    public SimulationViewModel(Simulation simulation, SimulationNavigationListener navigationListener)
    {
        this.simulation = simulation;
        this.context = simulation.getContext();
        this.navigationListener = navigationListener;

        for (Instruction instruction : context.getInstructions()) {
            instructionEntries.add(formatInstruction(instruction));
        }
    }

    private static String formatInstruction(Instruction instruction)
    {
        return String.format("%-2s   user %d   0x%X",
                instruction.getAccessType(), instruction.getUser(), instruction.getVirtualAddress());
    }

    /** Leaves the workbench and returns to the main menu; the simulation stays alive to resume. */
    public void navigateBack()
    {
        if (navigationListener != null) {
            navigationListener.onSimulationToMainMenu();
        }
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

    public ObservableList<String> getInstructionEntries()
    {
        return instructionEntries;
    }

    public StringProperty currentVirtualAddressHexProperty() 
    {
        return currentVirtualAddressHex;
    }

    public StringProperty currentPhysicalAddressHexProperty()
    {
        return currentPhysicalAddressHex;
    }

    public IntegerProperty currentInstructionIndexProperty()
    {
        return currentInstructionIndex;
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
            stepDescription.set(simulation.getLastStepDescription());
            logEntries.add("Step " + simulation.getCurrentStepNum() + ": " + simulation.getLastStepDescription());
        } catch (NoSuchElementException e) {
            stepDescription.set("End of instruction stream — nothing left to execute.");
            logEntries.add("Reached the end of the instruction stream.");
        } catch (Exception e) {
            stepDescription.set("Step engine is not yet wired up for this configuration.");
            logEntries.add("Next step failed: " + e.getMessage());
        } finally {
            // Sync the displayed counter to the simulation's own count no matter what: a step that
            // throws while building its description has still advanced the simulation's state.
            syncFromSimulation();
        }
    }

    public void executePreviousStep()
    {
        try {
            simulation.previousStep();
            stepDescription.set(simulation.getLastStepDescription());
            logEntries.add("Step " + (simulation.getCurrentStepNum() + 1) + " undone: " + simulation.getLastStepDescription());
        } catch (Exception e) {
            stepDescription.set("No earlier step to revert to.");
            logEntries.add("Previous step failed: " + e.getMessage());
        } finally {
            syncFromSimulation();
        }
    }

    private void syncFromSimulation()
    {
        currentStepNumber.set(simulation.getCurrentStepNum());
        refreshAddressDisplays();
    }

    private void refreshAddressDisplays()
    {
        if (!context.hasCurrentInstruction()) {
            currentInstructionIndex.set(-1);
            currentVirtualAddressHex.set("/");
            currentPhysicalAddressHex.set("/");
            return;
        }

        currentInstructionIndex.set(context.getCurrentInstructionIndex());
        currentVirtualAddressHex.set(String.format("0x%X", context.getCurrentInstruction().getVirtualAddress()));

        long physicalAddress = context.getCurrentPhysicalAddress();
        currentPhysicalAddressHex.set(physicalAddress < 0 ? "/" : String.format("0x%X", physicalAddress));
    }
}
