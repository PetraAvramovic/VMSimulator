package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Instruction.AccessType;
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

        if (entry != null && !entry.isDirty() && currentInstruction.getAccessType() == AccessType.WR)
            entry.setDirty(true);
        
        lastLookupResult = entry;

        return nextStep(entry);
    }


    @Override
    public void undo() 
    {

    }

    @Override
    public String getDescription()
    {
        return lastLookupResult != null
                ? String.format("TLB lookup: hit (frame 0x%X).", lastLookupResult.getBlock())
                : "TLB lookup: miss.";
    }

}
