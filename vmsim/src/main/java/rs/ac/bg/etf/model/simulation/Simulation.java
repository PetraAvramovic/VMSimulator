package rs.ac.bg.etf.model.simulation;

import java.util.Stack;

import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class Simulation<T extends SimulationContext> 
{
    private T context;
    private Stack<SimulationStep<T>> stepHistory = new Stack<>();

    
}
