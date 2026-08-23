package rs.ac.bg.etf.model.os;

import java.util.ArrayList;

public class FIFOEvictionPolicy extends EvictionPolicy
{
    private ArrayList<Long> queue = new ArrayList<>();

    @Override
    public void logAllocation(long address) 
    {
        queue.addLast(address);
    }

    @Override
    public long getVictim() 
    {
        long frame = queue.getFirst();

        return frame;
    }

    @Override
    public void undoVictim(long address)
    {
        queue.addFirst(address);
    }

    @Override
    public void undoAllocation(long address) 
    {
        queue.removeLast();
    }

}
