package rs.ac.bg.etf.model.simulation.step;

import java.util.NoSuchElementException;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class InstructionFetchStep<T extends SimulationContext> extends SimulationStep<T>
{
    // Captured on execute() so getStepDescription() stays valid after undo() has stepped the
    // instruction pointer back off this instruction (and possibly before the first one).
    private Instruction fetchedInstruction;

    // The previous instruction's physical address lingers in the context until this instruction's
    // own FormPhysicalAddressFrom*Step runs; without clearing it here, the MMU/Memory tabs would
    // keep showing the stale address for the whole gap between fetch and that step. Saved here so
    // undo() can restore exactly what was there before (the same pattern FormPhysicalAddressFrom*Step
    // itself already uses for its own save/restore).
    private long previousPhysicalAddress = -1;

    protected InstructionFetchStep(T context)
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute()
    {
        // Stepping the trailing fetch past the last instruction must be a clean stop: bail before
        // touching the instruction pointer so it never lands out of range.
        if (!context.hasNextInstruction())
            throw new NoSuchElementException("No further instructions to fetch.");

        context.nextInstruction();
        fetchedInstruction = context.getCurrentInstruction();

        previousPhysicalAddress = context.getCurrentPhysicalAddress();
        context.setCurrentPhysicalAddress(-1);

        return nextStep();
    }

    protected abstract SimulationStep<T> nextStep();

    @Override
    public void undo()
    {
        context.previousInstruction();
        context.setCurrentPhysicalAddress(previousPhysicalAddress);
    }

    @Override
    public boolean isFirst()
    {
        return true;
    }

    @Override
    public StepDescription getStepDescription()
    {
        Instruction instruction = fetchedInstruction != null ? fetchedInstruction : context.getCurrentInstruction();
        return new StepDescription(StepDescriptionKey.INSTRUCTION_FETCHED,
                instruction.getAccessType(), instruction.getVirtualAddress(), instruction.getUser());
    }

}
