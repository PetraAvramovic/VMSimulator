package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class InstructionFetchStep<T extends SimulationContext> extends SimulationStep<T>
{

    protected InstructionFetchStep(T context) 
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute() 
    {
        context.nextInstruction();
        
        return nextStep();
    }

    protected abstract SimulationStep<T> nextStep();

    @Override
    public void undo() 
    {
        context.previousInstruction();
    }

}
