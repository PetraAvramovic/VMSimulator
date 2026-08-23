package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.MemoryAccessStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class PageMemoryAccessStep<T extends PageSimulationContext> extends MemoryAccessStep<T> 
{

    protected PageMemoryAccessStep(T context) 
    {
        super(context);
    }

    @Override
    protected SimulationStep<T> nextStep() 
    {
       return new PageInstructionFetchStep<T>(context);
    }

    
    
}
