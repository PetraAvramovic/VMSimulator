package rs.ac.bg.etf.model.tlb;

/**
 * Immutable UI/validation helper describing a TLB configuration.
 * Not required by the TLB implementations themselves - they receive
 * their bit widths directly through their constructors - but useful for
 * configuration dialogs and for computing tags outside of a TLB instance.
 */
public final class TLBConfig
{
    private final TLBType type;
    private final int size;
    private final int associativity;
    private final int addressComponentBits;
    private final int processIdBits;
    private final TranslationMode mode;
    
    public TLBConfig(TLBType type, int size, int associativity, int addressComponentBits,
                      int processIdBits, TranslationMode mode)
    {
        this.type = type;
        this.size = size;
        this.associativity = associativity;
        this.addressComponentBits = addressComponentBits;
        this.processIdBits = processIdBits;
        this.mode = mode;
    }
    
    public TLBType getType()
    {
        return type;
    }
    
    public int getSize()
    {
        return size;
    }
    
    public int getAssociativity()
    {
        return associativity;
    }
    
    public int getAddressComponentBits()
    {
        return addressComponentBits;
    }
    
    public int getProcessIdBits()
    {
        return processIdBits;
    }
    
    public TranslationMode getMode()
    {
        return mode;
    }
    
    /**
     * Combines a process id and an address component into a single tag,
     * mirroring TLB.calculateTag() for use before a TLB instance exists.
     * @param processId The process id
     * @param addressComponent The address component (page number, segment number, etc.)
     * @return The combined tag
     */
    public int calculateTag(int processId, int addressComponent)
    {
        return (processId << addressComponentBits) | addressComponent;
    }
    
    @Override
    public String toString()
    {
        return String.format("TLBConfig[type=%s, size=%d, associativity=%d, addressComponentBits=%d, processIdBits=%d, mode=%s]",
                             type, size, associativity, addressComponentBits, processIdBits, mode);
    }
}
