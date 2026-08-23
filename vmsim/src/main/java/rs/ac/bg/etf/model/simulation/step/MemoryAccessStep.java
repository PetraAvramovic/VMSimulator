package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Memory;
import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class MemoryAccessStep<T extends SimulationContext> extends SimulationStep<T> 
{
    private long previousValue = -1L;

    protected MemoryAccessStep(T context) 
    {
        super(context);
    }

    protected abstract SimulationStep<T> nextStep();
    
    @Override
    public SimulationStep<T> execute()
    {
        Memory memory = context.getMemory();
        Instruction currentInstruction = context.getCurrentInstruction();
        long address = currentInstruction.getPhysicalAddress();

        switch (currentInstruction.getAccessType()) 
        {
            case Instruction.AccessType.RD:
                long readValue = memory.read(address);
                currentInstruction.setValue(readValue); 
                break;
        
            case Instruction.AccessType.WR:
                previousValue = memory.read(address);
                long writeValue = currentInstruction.getValue();
                memory.write(address, writeValue);
                break;

            case Instruction.AccessType.EX:
                memory.execute(address);
                break;
            default:
                break;
        }

        return nextStep();
    }

    @Override
    public void undo()
    {
        Instruction currentInstruction = context.getCurrentInstruction();
        Memory memory = context.getMemory();
        long address = currentInstruction.getPhysicalAddress();

        switch (currentInstruction.getAccessType()) {
            case Instruction.AccessType.RD:
                currentInstruction.setValue(0);
                break;
        
            case Instruction.AccessType.WR:
                memory.write(address, previousValue);

            default:
                break;
        }
    }
}
