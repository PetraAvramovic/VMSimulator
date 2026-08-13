package rs.ac.bg.etf.model.table;

import java.util.HashMap;
import java.util.Map;

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
    
    public PageTableDescriptor getEntry(long page)
    {
        PageTableDescriptor entry = entries.get(page);

        if (entry == null)
        {
            entry = new PageTableDescriptor(false, false, 0, 0);
            entry.setDisk(diskAddressGenerator.getDiskAddress((user << maxPages) + page));
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
