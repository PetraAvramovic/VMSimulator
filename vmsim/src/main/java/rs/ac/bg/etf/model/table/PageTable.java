package rs.ac.bg.etf.model.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import rs.ac.bg.etf.model.disk.DiskAddressGenerator;
import rs.ac.bg.etf.model.simulation.SimulationConfig;

public class PageTable 
{
    private Map<Long, PageTableDescriptor> entries = new HashMap<>();
    private DiskAddressGenerator diskAddressGenerator;
    private int user;
    private long maxPages;
    //private long startAddress;
    //private int descriptorSize;

    public PageTable(DiskAddressGenerator diskAddressGenerator, int user, long maxPages/*, long startAddress*/) 
    {
        this.diskAddressGenerator = diskAddressGenerator;
        this.user = user;
        this.maxPages = maxPages;
        //this.startAddress = startAddress;
    }

    public void init(Map<Long, SimulationConfig.PageTableDescriptorInit> pageTableInit)
    {
        if (pageTableInit == null)
            return;

        for (Map.Entry<Long, SimulationConfig.PageTableDescriptorInit> entry : pageTableInit.entrySet())
        {
            long page = entry.getKey();
            SimulationConfig.PageTableDescriptorInit initData = entry.getValue();

            long disk = diskAddressGenerator.getDiskAddress((user << maxPages) + page);
            PageTableDescriptor descriptor = new PageTableDescriptor(
                initData.valid(),
                initData.dirty(),
                initData.block(),
                disk,
                page
            );

            entries.put(page, descriptor);
        }
    }
    
    public PageTableDescriptor getEntryAndAdd(long page)
    {
        PageTableDescriptor entry = entries.get(page);

        if (entry == null)
        {
            entry = new PageTableDescriptor(false, false, 0, diskAddressGenerator.getDiskAddress((user << maxPages) + page), page);
            entries.put(page, entry);
        }

        return entry;
    }

    public PageTableDescriptor getEntry(long page)
    {
        PageTableDescriptor entry = entries.get(page);

        if (entry == null)
            entry = new PageTableDescriptor(false, false, 0, diskAddressGenerator.getDiskAddress((user << maxPages) + page), page);
    
        return entry;
    }

    public ArrayList<PageTableDescriptor> getValidEntries()
    {
        ArrayList<PageTableDescriptor> validEntries = new ArrayList<>();

        for (Long page: entries.keySet())
        {
            PageTableDescriptor descriptor = entries.get(page);
            if (descriptor.isValid())
                validEntries.add(descriptor);
        }

        return validEntries;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PageTable[user=%d, maxPages=%d]%n", user, maxPages));

        long displayLimit = Math.min(64, maxPages);

        for (long page = 0; page < displayLimit; page++)
        {
            PageTableDescriptor entry = entries.get(page);

            if (entry == null)
            {
                entry = new PageTableDescriptor(false, false, 0,
                    diskAddressGenerator.getDiskAddress((user << maxPages) + page), page);
            }

            sb.append(String.format("  [%d]: %s%n", page, entry.toString()));
        }

        return sb.toString();
    }
}
