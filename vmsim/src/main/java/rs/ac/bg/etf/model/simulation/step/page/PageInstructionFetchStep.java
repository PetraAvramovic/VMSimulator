package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.InstructionFetchStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class PageInstructionFetchStep<T extends PageSimulationContext> extends InstructionFetchStep<T> {

    protected PageInstructionFetchStep(T context) 
    {
        super(context);
    }

    @Override
    protected SimulationStep<T> nextStep() 
    {
        return new PageTLBLookupStep<T>(context);
    }

}
