package rs.ac.bg.etf.model.disk;

import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;

public class Disk 
{
    private HashMap<Long, SortedMap<Long, Long>> blocks = new HashMap<>(); 

    public void init(HashMap<Long, SortedMap<Long, Long>> diskInit)
    {
        
    }

    public SortedMap<Long, Long> readBlock(long address)
    {
        return new TreeMap<>(blocks.getOrDefault(address, new TreeMap<>()));
    }

    public void writeBlock(long address, SortedMap<Long, Long> block)
    {
        blocks.put(address, block);
    }
}
