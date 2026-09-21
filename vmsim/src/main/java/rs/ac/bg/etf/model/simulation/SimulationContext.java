package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.disk.DiskAddressGenerator;
import rs.ac.bg.etf.model.memory.*;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
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

    // Identity of whichever entry TLBEvictionStep most recently evicted to make room for an
    // insertion, set in its execute() and read by consumers (e.g. the TLB tab's notification
    // side note) -- the entry itself stays in the TLB's own storage (just marked invalid), so its
    // tag/block/index are readable directly from there too, but wasDirty specifically reflects its
    // state at the moment of eviction, since the step itself clears the live entry's dirty flag.
    private long evictedTlbTag = -1;
    private long evictedTlbBlock = -1;
    private boolean evictedTlbWasDirty = false;
    private int evictedTlbIndex = -1;

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
        disk = new Disk();

        memory.init(config.getMemoryInit());
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

    protected abstract void initDisk();

    public abstract SimulationStep<? extends SimulationContext> getFirstStep(); 

    public long getCurrentPhysicalAddress() {
        return currentPhysicalAddress;
    }

    public void setCurrentPhysicalAddress(long currentPhysicalAddress) {
        this.currentPhysicalAddress = currentPhysicalAddress;
    }

    public void setEvictedTlbEntry(long tag, long block, boolean wasDirty, int index)
    {
        this.evictedTlbTag = tag;
        this.evictedTlbBlock = block;
        this.evictedTlbWasDirty = wasDirty;
        this.evictedTlbIndex = index;
    }

    public long getEvictedTlbTag()
    {
        return evictedTlbTag;
    }

    public long getEvictedTlbBlock()
    {
        return evictedTlbBlock;
    }

    public boolean wasEvictedTlbDirty()
    {
        return evictedTlbWasDirty;
    }

    public int getEvictedTlbIndex()
    {
        return evictedTlbIndex;
    }


    public TLB getTLB()
    {
        return tlb;
    }

    public SimulationConfig.TLBType getTlbType()
    {
        return config.getTlbType();
    }

    public int getWordBits()
    {
        return config.getWordBits();
    }

    // Despite the name, this is only the page/index component (the full virtual address minus its
    // word-offset bits) -- the width TLB/page-table address components are built from. See
    // getFullVirtualAddressBits() for the complete virtual address width instructions are given in.
    public int getVirtualAddressBits()
    {
        return config.getVirtualMemoryBits() - config.getWordBits();
    }

    /** Full virtual address width (page/index component + word offset), e.g. for padding a raw
     *  instruction address's hex display -- unlike getVirtualAddressBits(), which excludes the word
     *  offset. */
    public int getFullVirtualAddressBits()
    {
        return config.getVirtualMemoryBits();
    }

    public int getAddressableUnit()
    {
        return config.getAddressableUnit();
    }

    public int getPhysicalAddressBits()
    {
        return config.getPhysicalAddressBits();
    }

    public int getNumberOfUsers()
    {
        return numberOfUsers;
    }

    public int getDiskBits()
    {
        return config.getDiskBits();
    }

    /**
     * The current instruction's virtual address without its word-offset bits: the page number when
     * paged. Together with the user it is what a TLB lookup key is built from.
     */
    public long getAddressComponent()
    {
        return getCurrentInstruction().getVirtualAddress() >> config.getWordBits();
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

    public List<Instruction> getInstructions()
    {
        return Collections.unmodifiableList(instructions);
    }

    public int getCurrentInstructionIndex()
    {
        return currentInstructionInd;
    }

    public boolean hasCurrentInstruction()
    {
        return currentInstructionInd >= 0 && currentInstructionInd < instructions.size();
    }

    public boolean hasNextInstruction()
    {
        return currentInstructionInd + 1 < instructions.size();
    }

    public void nextInstruction()
    {
        if (currentInstructionInd < instructions.size() - 1)
            currentInstructionInd++;
    }

    public void previousInstruction()
    {
        if (currentInstructionInd >= 0)
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

    public long getValueAtAddress(long address)
    {
        return memory.read(address);
    }

    public long getPhysicalMemorySize()
    {
        return 1L << config.getPhysicalAddressBits();
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
