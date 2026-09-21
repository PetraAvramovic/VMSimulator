package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBLookupStep<T extends SimulationContext> extends SimulationStep<T> 
{
    private TLBEntry lastLookupResult;

    // What was looked up, captured in execute() for the description: the user and page the key was
    // built from, and the full key itself.
    private int lookedUpUser;
    private long lookedUpComponent;
    private long lookedUpKey;

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

        lookedUpUser = user;
        lookedUpComponent = addressComponent;
        lookedUpKey = tag;
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
                ? new StepDescription(StepDescriptionKey.TLB_LOOKUP_HIT, lookedUpComponent, lookedUpUser,
                        describeTlbEntry(lastLookupResult), lastLookupResult.getBlock())
                : new StepDescription(StepDescriptionKey.TLB_LOOKUP_MISS, lookedUpComponent, lookedUpUser,
                        describeSearched());
    }

    // What the key was compared against: the one entry a direct-mapped TLB maps it to, the one set of a
    // set-associative TLB, or every entry of a fully-associative one.
    private StepDescription describeSearched()
    {
        TLB tlb = context.getTLB();

        int slot = tlb.mappedSlot(lookedUpKey);
        if (slot >= 0)
            return describeTlbSlot(slot);

        int set = tlb.setIndexOf(lookedUpKey);
        return set >= 0
                ? new StepDescription(StepDescriptionKey.TLB_SEARCHED_SET, set)
                : new StepDescription(StepDescriptionKey.TLB_SEARCHED_ALL);
    }

}
