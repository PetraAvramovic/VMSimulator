package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBLookupStep<T extends SimulationContext> extends SimulationStep<T> 
{
    private TLBEntry lastLookupResult;

    protected TLBLookupStep(T context) 
    {
        super(context);
    }

    public abstract SimulationStep<T> nextStep(TLBEntry entry);

    @Override
    public SimulationStep<T> execute()
    {
        TLB tlb = context.getTLB();
        Instruction currentInstruction = context.getCurrentInstruction();

        int user = currentInstruction.getUser();
        long addressComponent = currentInstruction.getVirtualAddress() >> context.getWordBits();

        long tag = tlb.calculateTag(user, addressComponent);
        TLBEntry entry = tlb.lookup(tag);

        
        
        lastLookupResult = entry;

        setAffectedComponents(SimulationComponent.TLB);
        return nextStep(entry);
    }


    @Override
    public void undo() 
    {

    }

    @Override
    public StepDescription getStepDescription()
    {
        return lastLookupResult != null
                ? new StepDescription(StepDescriptionKey.TLB_LOOKUP_HIT, lastLookupResult.getBlock())
                : new StepDescription(StepDescriptionKey.TLB_LOOKUP_MISS);
    }

}
