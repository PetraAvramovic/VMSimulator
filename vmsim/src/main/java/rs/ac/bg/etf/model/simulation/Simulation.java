package rs.ac.bg.etf.model.simulation;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class Simulation 
{
    private SimulationContext context;
   
    private Stack<SimulationStep<? extends SimulationContext>> stepHistory = new Stack<>();
    private SimulationStep<? extends SimulationContext> currentStep = null;
    private int currentStepNum = 0;
    private String lastStepDescription = "";
        

    public Simulation(SimulationContext context) 
    {
        this.context = context;
    }

    public void init()
    {
        stepHistory.clear();
        context.init();
        currentStep = context.getFirstStep();
        currentStepNum = 0;
        lastStepDescription = "";
    }

    public void nextStep()
    {
        SimulationStep<? extends SimulationContext> executedStep = currentStep;

        // Push only after a successful execute(), otherwise a step that throws mid-way (e.g. stepping
        // past the last instruction) would leave a half-applied entry on the undo stack.
        SimulationStep<? extends SimulationContext> next = executedStep.execute();
        stepHistory.push(executedStep);
        currentStep = next;
        currentStepNum++;
        lastStepDescription = executedStep.getDescription();
    }

    public void previousStep()
    {
        // Pop and decrement together so currentStepNum stays equal to stepHistory.size() even if
        // undo() or getDescription() below throws -- otherwise the counter and the stack drift apart.
        currentStep = stepHistory.pop();
        currentStepNum--;
        currentStep.undo();
        lastStepDescription = "Undid: " + currentStep.getDescription();
    }

    public SimulationContext getContext()
    {
        return context;
    }

    public int getCurrentStepNum()
    {
        return currentStepNum;
    }

    public String getLastStepDescription()
    {
        return lastStepDescription;
    }

    // Executed steps in order, oldest first; lets views infer what happened since e.g. the last fetch
    public List<SimulationStep<? extends SimulationContext>> getExecutedSteps()
    {
        return Collections.unmodifiableList(stepHistory);
    }

}
