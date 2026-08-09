package rs.ac.bg.etf.model.memory;

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
    private long address;
    private long value = 0L;

    public Instruction(int user, AccessType accessType, long address) {
        this.user = user;
        this.accessType = accessType;
        this.address = address;
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

    public long getAddress() 
    {
        return address;
    }

    public long getValue() 
    {
        return value;
    }
    
    public void setValue(long value) 
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        if (accessType == AccessType.WR)
        {
            return String.format("Instruction[user=%d, accessType=%s, address=0x%x, value=0x%x]",
                                 user, accessType, address, value);
        }
        return String.format("Instruction[user=%d, accessType=%s, address=0x%x]",
                             user, accessType, address);
    }
}
