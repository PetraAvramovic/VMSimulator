package rs.ac.bg.etf.model.table;

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

    public PageTable(DiskAddressGenerator diskAddressGenerator, int user, long maxPages) 
    {
        this.diskAddressGenerator = diskAddressGenerator;
        this.user = user;
        this.maxPages = maxPages;
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
                disk
            );

            entries.put(page, descriptor);
        }
    }
    
    public PageTableDescriptor getEntry(long page)
    {
        PageTableDescriptor entry = entries.get(page);

        if (entry == null)
        {
            entry = new PageTableDescriptor(false, false, 0, 0);
            entry.setDisk(diskAddressGenerator.getDiskAddress((user << maxPages) + page));
            entries.put(page, entry);
        }

        return entry;
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
                    diskAddressGenerator.getDiskAddress((user << maxPages) + page));
            }

            sb.append(String.format("  [%d]: %s%n", page, entry.toString()));
        }

        return sb.toString();
    }
}
