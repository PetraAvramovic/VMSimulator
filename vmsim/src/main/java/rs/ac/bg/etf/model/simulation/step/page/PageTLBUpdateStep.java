package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBUpdateStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageTLBUpdateStep<T extends PageSimulationContext> extends TLBUpdateStep<T>
{
    private PageTableDescriptor descriptor;

    public PageTLBUpdateStep(T context, PageTableDescriptor descriptor)
    {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public TLBEntry getTLBEntry()
    {
        Instruction instruction = context.getCurrentInstruction();
        int user = instruction.getUser();
        long tag = context.getTLB().calculateTag(user, context.getPageComponent());
        TLBEntry entry = new TLBEntry(tag, true, descriptor.isDirty(), descriptor.getBlock());

        return entry;
    }

    @Override
    public SimulationStep<T> nextStep()
    {
        return new PageMemoryAccessStep<T>(context);
    }

    public static <U extends PageSimulationContext> SimulationStep<U> nextTlbStep(U context, PageTableDescriptor descriptor)
    {
        int user = context.getCurrentInstruction().getUser();
        long tag = context.getTLB().calculateTag(user, context.getPageComponent());

        if (context.getTLB().wouldEvict(tag))
            return new PageTLBEvictionStep<U>(context, descriptor);

        return new PageTLBUpdateStep<U>(context, descriptor);
    }
}
