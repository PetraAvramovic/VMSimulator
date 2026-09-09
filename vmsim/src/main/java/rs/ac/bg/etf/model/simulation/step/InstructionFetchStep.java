package rs.ac.bg.etf.model.simulation.step;

import java.util.NoSuchElementException;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class InstructionFetchStep<T extends SimulationContext> extends SimulationStep<T>
{
    // Captured on execute() so getDescription() stays valid after undo() has stepped the
    // instruction pointer back off this instruction (and possibly before the first one).
    private Instruction fetchedInstruction;

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

        return nextStep();
    }

    protected abstract SimulationStep<T> nextStep();

    @Override
    public void undo()
    {
        context.previousInstruction();
    }

    @Override
    public String getDescription()
    {
        Instruction instruction = fetchedInstruction != null ? fetchedInstruction : context.getCurrentInstruction();
        return String.format("Fetched instruction: %s 0x%X (user %d).",
                instruction.getAccessType(), instruction.getVirtualAddress(), instruction.getUser());
    }

}
