package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.disk.DiskAddressGenerator;
import rs.ac.bg.etf.model.memory.*;
import rs.ac.bg.etf.model.tlb.AssociativeTLB;
import rs.ac.bg.etf.model.tlb.DirectTLB;
import rs.ac.bg.etf.model.tlb.SetAssociativeTLB;
import rs.ac.bg.etf.model.tlb.TLB;

public abstract class SimulationContext 
{
    protected SimulationConfig config;
    protected DiskAddressGenerator diskAddressGenerator;
    protected int numberOfUsers;
    protected TLB tlb;
    protected Disk disk;

    private Memory memory;
    private ArrayList<Instruction> instructions;
    private int currentInstructionInd = -1;
    private long currentPhysicalAddress = -1;

    public long getCurrentPhysicalAddress() {
        return currentPhysicalAddress;
    }

    public void setCurrentPhysicalAddress(long currentPhysicalAddress) {
        this.currentPhysicalAddress = currentPhysicalAddress;
    }

    public SimulationContext(SimulationConfig config) 
    {
        this.config = config;
    }

    public void init()
    {
        numberOfUsers = config.getNumberOfUsers();
        memory = new Memory(config.getMemorySize());
        instructions = config.getInstructions();
        diskAddressGenerator = new DiskAddressGenerator(config.generateDiskSeed());
        initTLB();
    }

    protected void initTLB()
    {
        int addressBits = config.getVirtualMemoryBits() - config.getWordBits();
        int processBits = Integer.bitCount(config.getNumberOfUsers() - 1);
        int tlbSize = config.getTlbSize();

        switch (config.getTlbType())
        {
            case ASSOCIATIVE:
                tlb = new AssociativeTLB(tlbSize, addressBits, processBits);
                break;
            case DIRECT:
                tlb = new DirectTLB(tlbSize, addressBits, processBits);
                break;
            case SET_ASSOCIATIVE:
                int tlbEntriesPerSet = config.getTlbEntriesPerSet();
                tlb = new SetAssociativeTLB(tlbSize, addressBits, processBits, tlbEntriesPerSet);
                break;
        }

    }
    
    public TLB getTLB()
    {
        return tlb;
    }

    public int getWordBits()
    {
        return config.getWordBits();
    }

    public int getVirtualAddressBits()
    {
        return config.getVirtualMemoryBits() - config.getWordBits();
    }

    public int getAddressableUnit()
    {
        return config.getAddressableUnit();
    }

    public long getWordComponent()
    {
        Instruction instruction = getCurrentInstruction();
        long address = instruction.getVirtualAddress();
        int wordBits = config.getWordBits();
        return address & ((1L << wordBits) - 1);
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

    public Disk getDisk()
    {
        return disk;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "SimulationContext[config=%s, diskAddressGenerator=%s, numberOfUsers=%d, " +
            "memory=%s, currentInstructionInd=%d]",
            config, diskAddressGenerator, numberOfUsers, memory, currentInstructionInd));
        
        if (instructions != null)
        {
            sb.append(String.format("%n  Instructions (%d):", instructions.size()));
            for (int i = 0; i < instructions.size(); i++)
            {
                sb.append(String.format("%n    [%d]: %s", i, instructions.get(i).toString()));
            }
        }
        
        return sb.toString();
    }
}
