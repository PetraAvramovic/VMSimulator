package rs.ac.bg.etf.model.os;


public abstract class OSMemoryManager 
{
    protected EvictionPolicy evictionPolicy;
    protected int numberOfUsers;

    public OSMemoryManager(EvictionPolicy evictionPolicy, int numberOfUsers) 
    {
        this.evictionPolicy = evictionPolicy;
        this.numberOfUsers = numberOfUsers;
    }

    public EvictionPolicy getEvictionPolicy()
    {
        return evictionPolicy;
    }

}
