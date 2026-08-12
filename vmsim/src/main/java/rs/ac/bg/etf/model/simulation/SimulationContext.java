package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;

import rs.ac.bg.etf.model.memory.*;

public abstract class SimulationContext 
{
    protected SimulationConfig config;
    private Memory memory;
    private ArrayList<Instruction> instructions;
    private int currentInstructionInd = -1;

    public SimulationContext(SimulationConfig config) 
    {
        this.config = config;
    }

    public void init()
    {
        memory = new Memory(config.getMemorySize());
        
    }

    public Instruction getCurrentInstruction()
    {
        return instructions.get(currentInstructionInd);
    }

    public void nextInstruction()
    {
        if (currentInstructionInd < instructions.size())
            currentInstructionInd++;
    }

    public void previousInstruction()
    {
        if (currentInstructionInd > 0)
            currentInstructionInd--;
    }

    public Memory getMemory() 
    {
        return memory;
    }

    
}
