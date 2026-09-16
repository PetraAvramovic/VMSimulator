package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;

public class Simulation
{
    private SimulationContext context;

    private Stack<SimulationStep<? extends SimulationContext>> stepHistory = new Stack<>();
    // Kept in lockstep with stepHistory (pushed/popped in the same two methods, right next to
    // it): each step's description is computed once, when it executes, and cached here rather
    // than re-derived from stepHistory on every read -- see getStepLog()/getCurrentStepDescription().
    private ArrayList<StepDescription> stepLog = new ArrayList<>();
    private SimulationStep<? extends SimulationContext> currentStep = null;
    private int currentStepNum = 0;


    public Simulation(SimulationContext context)
    {
        this.context = context;
    }

    public void init()
    {
        stepHistory.clear();
        stepLog.clear();
        context.init();
        currentStep = context.getFirstStep();
        currentStepNum = 0;
    }

    public void nextStep()
    {
        SimulationStep<? extends SimulationContext> executedStep = currentStep;

        // Push only after a successful execute(), otherwise a step that throws mid-way (e.g. stepping
        // past the last instruction) would leave a half-applied entry on the undo stack.
        SimulationStep<? extends SimulationContext> next = executedStep.execute();
        stepHistory.push(executedStep);
        stepLog.add(executedStep.getStepDescription());
        currentStep = next;
        currentStepNum++;
    }

    public void previousStep()
    {
        // Pop and decrement together so currentStepNum stays equal to stepHistory.size() even if
        // undo() below throws -- otherwise the counter and the stack drift apart.
        currentStep = stepHistory.pop();
        stepLog.remove(stepLog.size() - 1);
        currentStepNum--;
        currentStep.undo();
    }

    /** Undoes the last {@code n} steps, most-recent-first (no-op if n <= 0). */
    public void previousSteps(int n)
    {
        for (int i = 0; i < n; i++)
            previousStep();
    }

    public SimulationContext getContext()
    {
        return context;
    }

    public int getCurrentStepNum()
    {
        return currentStepNum;
    }

    /** Description of whichever step is now on top of stepHistory, or null if none has run yet. */
    public StepDescription getCurrentStepDescription()
    {
        return stepLog.isEmpty() ? null : stepLog.get(stepLog.size() - 1);
    }

    /** Every executed step's description, oldest first -- an O(1) view, not a copy/recompute. */
    public List<StepDescription> getStepLog()
    {
        return Collections.unmodifiableList(stepLog);
    }

    // Executed steps in order, oldest first; lets views infer what happened since e.g. the last fetch
    public List<SimulationStep<? extends SimulationContext>> getExecutedSteps()
    {
        return Collections.unmodifiableList(stepHistory);
    }

    /** True if the step most recently executed (top of stepHistory) starts a new instruction. */
    public boolean isCurrentStepFirst()
    {
        return !stepHistory.isEmpty() && stepHistory.peek().isFirst();
    }

    /**
     * 1-based step number of the closest instruction-start step at or before {@code beforeStepNum}
     * (inclusive), i.e. the fetch step of whichever instruction was running at that point in the
     * log. Returns 0 if there is none (nothing has run yet, or {@code beforeStepNum <= 0}).
     */
    public int lastInstructionStartAtOrBefore(int beforeStepNum)
    {
        for (int i = Math.min(beforeStepNum, stepHistory.size()) - 1; i >= 0; i--)
            if (stepHistory.get(i).isFirst())
                return i + 1;
        return 0;
    }

    /**
     * 1-based step number of the (0-based) {@code instructionIndex}-th instruction's fetch step,
     * i.e. the (instructionIndex + 1)-th instruction-start step in execution order. Returns 0 if
     * that instruction hasn't been fetched yet (or {@code instructionIndex < 0}).
     */
    public int instructionStartStepNum(int instructionIndex)
    {
        if (instructionIndex < 0)
            return 0;

        int seen = -1;
        for (int i = 0; i < stepHistory.size(); i++)
        {
            if (stepHistory.get(i).isFirst())
            {
                seen++;
                if (seen == instructionIndex)
                    return i + 1;
            }
        }
        return 0;
    }

}
