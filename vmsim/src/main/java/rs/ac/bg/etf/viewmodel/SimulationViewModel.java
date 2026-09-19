package rs.ac.bg.etf.viewmodel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.listeners.SimulationNavigationListener;

/**
 * Outer-shell ViewModel for the simulation workbench (step navigation, log, MMU/TLB/OS tab hosting).
 */
public class SimulationViewModel
{
    private static final String READY_MESSAGE =
            "Simulation ready. Press \"Next\" to begin stepping through execution.";

    private final Simulation simulation;
    private final SimulationContext context;
    private final SimulationNavigationListener navigationListener;

    private final IntegerProperty currentStepNumber = new SimpleIntegerProperty(0);
    // The structured, locale-free description of whichever step is now current (null = no
    // current step, i.e. fresh sim or undone back to the start). Only the View formats this.
    private final ObjectProperty<StepDescription> currentStepDescription = new SimpleObjectProperty<>(null);
    // ViewModel-authored UI/error text for the non-step states (ready/end-of-stream/no-earlier-step);
    // shown on the step-description label only while currentStepDescription is null. Plain
    // hardcoded English for now -- out of scope for localization (see bugs.md/plan discussion).
    private final StringProperty fallbackMessage = new SimpleStringProperty(READY_MESSAGE);
    private final ObservableList<StepDescription> logEntries = FXCollections.observableArrayList();
    // Raw model objects, not pre-formatted strings: the view lays these out as its own columns
    // (Index/Op/User/VA), so formatting -- like everywhere else -- stays a view-layer concern.
    private final ObservableList<Instruction> instructionEntries = FXCollections.observableArrayList();

    private final StringProperty currentVirtualAddressHex = new SimpleStringProperty("/");
    private final StringProperty currentPhysicalAddressHex = new SimpleStringProperty("/");

    /** Row index into {@link #instructionEntries} of the instruction currently being executed, or -1. */
    private final IntegerProperty currentInstructionIndex = new SimpleIntegerProperty(-1);

    // Which tab is currently focused (fed by the view from its TabPane selection), and, per
    // component, whether its badge should show right now. A badge is only ever eligible while its
    // component is still within the "steps since the last instruction fetch" window (the same
    // window PagedMMUTabViewModel/PagedTLBTabViewModel's recomputeActiveLines() use) -- it clears
    // on its own once that window moves past it, same as those wires do. Independently, the
    // moment the user actually focuses a tab while it's eligible, that specific occurrence is
    // acknowledged and won't reappear just from switching away and back -- but a genuinely new
    // occurrence (the window going from "not touching this component" to "touching it" again)
    // overrides that and shows a fresh badge regardless. See updateTabNotifications().
    private final ObjectProperty<SimulationComponent> selectedComponent = new SimpleObjectProperty<>(null);
    private final Map<SimulationComponent, BooleanProperty> tabNotifications = new EnumMap<>(SimulationComponent.class);
    private final EnumSet<SimulationComponent> currentlyRelevant = EnumSet.noneOf(SimulationComponent.class);
    private final EnumSet<SimulationComponent> acknowledged = EnumSet.noneOf(SimulationComponent.class);

    public SimulationViewModel(Simulation simulation, SimulationNavigationListener navigationListener)
    {
        this.simulation = simulation;
        this.context = simulation.getContext();
        this.navigationListener = navigationListener;

        instructionEntries.addAll(context.getInstructions());

        for (SimulationComponent component : SimulationComponent.values())
            tabNotifications.put(component, new SimpleBooleanProperty(false));
        // Focusing a tab acknowledges whatever occurrence is currently relevant to it (if any) --
        // updateTabNotifications() is what actually raises a *new* occurrence back up regardless.
        selectedComponent.addListener((obs, old, sel) -> {
            if (sel != null)
                acknowledged.add(sel);
            refreshTabNotificationVisibility();
        });
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

    /** The structured description of the current step; null while there is no current step. */
    public ObjectProperty<StepDescription> currentStepDescriptionProperty()
    {
        return currentStepDescription;
    }

    /** ViewModel-authored fallback text shown only while {@link #currentStepDescriptionProperty()} is null. */
    public StringProperty fallbackMessageProperty()
    {
        return fallbackMessage;
    }

    public ObservableList<StepDescription> getLogEntries()
    {
        return logEntries;
    }

    public ObservableList<Instruction> getInstructionEntries()
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

    /** The tab the view currently has focused; the view is responsible for keeping this in sync
     *  with its TabPane selection. */
    public ObjectProperty<SimulationComponent> selectedComponentProperty()
    {
        return selectedComponent;
    }

    /** True while {@code component} was touched by the current step and isn't the focused tab. */
    public BooleanProperty tabNotificationProperty(SimulationComponent component)
    {
        return tabNotifications.get(component);
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
            // Mirrors Simulation's own push, incrementally -- no full-list resync, no policy
            // decisions here: an entry exists if and only if nextStep() succeeded.
            StepDescription description = simulation.getCurrentStepDescription();
            currentStepDescription.set(description);
            logEntries.add(description);
        } catch (NoSuchElementException e) {
            currentStepDescription.set(null);
            fallbackMessage.set("End of instruction stream — nothing left to execute.");
        } catch (Exception e) {
            currentStepDescription.set(null);
            fallbackMessage.set("Step engine is not yet wired up for this configuration.");
        } finally {
            // Sync the displayed counter to the simulation's own count no matter what: a step that
            // throws while executing has still advanced the simulation's state.
            syncFromSimulation();
        }
    }

    /**
     * Advances to the start of the next instruction: like {@link #executeNextStep()} repeated in
     * a loop, but only refreshes the displayed counters once at the end. Always moves forward by
     * at least one whole instruction, even if already sitting at one's very first step.
     */
    public void executeNextInstruction()
    {
        try {
            do {
                simulation.nextStep();
                StepDescription description = simulation.getCurrentStepDescription();
                currentStepDescription.set(description);
                logEntries.add(description);
            } while (!simulation.isCurrentStepFirst());
        } catch (NoSuchElementException e) {
            currentStepDescription.set(null);
            fallbackMessage.set("End of instruction stream — nothing left to execute.");
        } catch (Exception e) {
            currentStepDescription.set(null);
            fallbackMessage.set("Step engine is not yet wired up for this configuration.");
        } finally {
            syncFromSimulation();
        }
    }

    public void executePreviousStep()
    {
        try {
            simulation.previousStep();
            // Mirrors Simulation's own pop, incrementally: the entry for the step just undone
            // disappears, and the label falls back to whatever step is now current (or the
            // ready placeholder if none is).
            logEntries.remove(logEntries.size() - 1);
            StepDescription description = simulation.getCurrentStepDescription();
            currentStepDescription.set(description);
            if (description == null)
                fallbackMessage.set(READY_MESSAGE);
        } catch (Exception e) {
            currentStepDescription.set(null);
            fallbackMessage.set("No earlier step to revert to.");
        } finally {
            syncFromSimulation();
        }
    }

    /**
     * Reverts the simulation to the state right after the step numbered {@code targetStepNumber}
     * ran (1-based, matching the "Step N" label). No-op if that step is already current or later.
     */
    public void revertToStep(int targetStepNumber)
    {
        int stepsToUndo = simulation.getCurrentStepNum() - targetStepNumber;
        if (stepsToUndo <= 0)
            return;

        try {
            simulation.previousSteps(stepsToUndo);
        } catch (Exception e) {
            currentStepDescription.set(null);
            fallbackMessage.set("No earlier step to revert to.");
        } finally {
            // Recomputed from the simulation's own count, not trusted to equal targetStepNumber:
            // if undo() threw partway through, this still lands on wherever it actually stopped.
            int newCount = simulation.getCurrentStepNum();
            if (newCount < logEntries.size())
                logEntries.remove(newCount, logEntries.size());
            StepDescription description = simulation.getCurrentStepDescription();
            currentStepDescription.set(description);
            if (description == null)
                fallbackMessage.set(READY_MESSAGE);
            syncFromSimulation();
        }
    }

    /**
     * Reverts to the start of the current instruction (undoes everything run since its fetch
     * step) -- or, if already sitting right at that fetch step with nothing since it to undo, to
     * the start of the previous instruction instead, so repeated clicks keep moving backward one
     * instruction at a time rather than getting stuck as a no-op.
     */
    public void revertToInstructionStart()
    {
        int currentStepNum = simulation.getCurrentStepNum();
        int currentStart = simulation.lastInstructionStartAtOrBefore(currentStepNum);
        int target = currentStart == currentStepNum
                ? simulation.lastInstructionStartAtOrBefore(currentStepNum - 1)
                : currentStart;
        revertToStep(target);
    }

    /**
     * Jumps to the start of instruction {@code instructionIndex} (0-based, matching the
     * Instructions list's own Index column): reverts backward if it has already run, or executes
     * whole instructions forward if it hasn't been reached yet. No-op if already sitting exactly
     * there.
     */
    public void goToInstructionStart(int instructionIndex)
    {
        int startStep = simulation.instructionStartStepNum(instructionIndex);
        if (startStep > 0) {
            revertToStep(startStep);
            return;
        }

        // Not fetched yet: run whole instructions forward until it is, or the stream runs out
        // (executeNextInstruction() making no further progress signals that).
        while (simulation.instructionStartStepNum(instructionIndex) <= 0) {
            int before = simulation.getCurrentStepNum();
            executeNextInstruction();
            if (simulation.getCurrentStepNum() == before)
                return;
        }
    }

    private void syncFromSimulation()
    {
        currentStepNumber.set(simulation.getCurrentStepNum());
        refreshAddressDisplays();
        updateTabNotifications();
    }

    /**
     * Recomputes which components are relevant right now -- exactly the affected components of
     * whichever single step is currently on top of the step history (the current step only, not
     * an instruction-wide window: unlike the MMU/TLB wires' recomputeActiveLines(), a badge is
     * about "what did *this* step just do," not "what's happened so far this instruction") --
     * then reconciles that against what was relevant last time: a component that just became
     * relevant (it wasn't a moment ago) is a *new* occurrence and un-acknowledges it, so its badge
     * shows even if the user had previously dismissed an earlier occurrence for that same tab. A
     * component that stops being relevant (the very next step, whatever it is, doesn't touch it)
     * simply stops showing, on its own -- no per-tab bookkeeping needed for that direction, since
     * the badge is gated on {@code currentlyRelevant} either way.
     *
     * If the newly relevant component is the one currently focused, it's marked acknowledged
     * immediately: the user is watching it happen live, so there's nothing to notify about, and
     * without this, moving focus away afterward would otherwise surface a notification for an
     * occurrence they'd already seen (the sole reason it wasn't showing until then was
     * "focused," which stops applying the moment focus moves elsewhere).
     */
    private void updateTabNotifications()
    {
        EnumSet<SimulationComponent> relevant = EnumSet.noneOf(SimulationComponent.class);
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        if (!history.isEmpty())
            relevant.addAll(history.get(history.size() - 1).getAffectedComponents());

        for (SimulationComponent component : SimulationComponent.values())
            if (relevant.contains(component) != currentlyRelevant.contains(component))
                acknowledged.remove(component);
        currentlyRelevant.clear();
        currentlyRelevant.addAll(relevant);

        SimulationComponent focused = selectedComponent.get();
        if (focused != null && currentlyRelevant.contains(focused))
            acknowledged.add(focused);

        refreshTabNotificationVisibility();
    }

    /** Badge = relevant right now, not the focused tab (already visible there), not acknowledged. */
    private void refreshTabNotificationVisibility()
    {
        SimulationComponent focused = selectedComponent.get();
        for (SimulationComponent component : SimulationComponent.values())
            tabNotifications.get(component).set(
                    currentlyRelevant.contains(component) && component != focused && !acknowledged.contains(component));
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
        currentVirtualAddressHex.set(ValueConverter.toHex(
                context.getCurrentInstruction().getVirtualAddress(),
                ValueConverter.hexDigitsFor(context.getFullVirtualAddressBits())));

        long physicalAddress = context.getCurrentPhysicalAddress();
        currentPhysicalAddressHex.set(physicalAddress < 0 ? "/" : ValueConverter.toHex(
                physicalAddress, ValueConverter.hexDigitsFor(context.getPhysicalAddressBits())));
    }
}
