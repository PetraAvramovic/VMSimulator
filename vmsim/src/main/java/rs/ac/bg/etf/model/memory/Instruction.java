package rs.ac.bg.etf.model.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Instruction 
{
    public enum AccessType
    {
        RD,
        WR,
        EX
    }
    
    private int user;
    private AccessType accessType;
    private long virtualAddress;
    private long physicalAddress = -1;

    private long value = 0L;

    @JsonCreator
    public Instruction(
        @JsonProperty("user") int user, 
        @JsonProperty("accessType") AccessType accessType, 
        @JsonProperty("virtualAddress") long virtualAddress
    ) {
        this.user = user;
        this.accessType = accessType;
        this.virtualAddress = virtualAddress;
    }

    public Instruction(int user, AccessType accessType, long address, long value) {
        this(user, accessType, address);
        this.value = value;
    }

    public int getUser() 
    {
        return user;
    }

    public AccessType getAccessType() 
    {
        return accessType;
    }

    public long getVirtualAddress() 
    {
        return virtualAddress;
    }

    public long getValue() 
    {
        return value;
    }
    
    public void setValue(long value) 
    {
        this.value = value;
    }

    public long getPhysicalAddress() {
        return physicalAddress;
    }

    public void setPhysicalAddress(long physicalAddress) {
        this.physicalAddress = physicalAddress;
    }

    @Override
    public String toString()
    {
        if (accessType == AccessType.WR)
        {
            return String.format("Instruction[user=%d, accessType=%s, address=0x%x, value=0x%x]",
                                 user, accessType, virtualAddress, value);
        }
        return String.format("Instruction[user=%d, accessType=%s, address=0x%x]",
                             user, accessType, virtualAddress);
    }
}
