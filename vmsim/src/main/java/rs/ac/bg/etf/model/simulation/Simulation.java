package rs.ac.bg.etf.model.simulation;

import java.util.Stack;

import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class Simulation<T extends SimulationContext> 
{
    private T context;
   
    private Stack<SimulationStep<T>> stepHistory = new Stack<>();
    private SimulationStep<T> currentStep = null;
    private int currentStepNum = -1;
        

    public Simulation(T context) 
    {
        this.context = context;
    }

    public void init()
    {
        stepHistory.clear();
        context.init();
        
    }
    
}
