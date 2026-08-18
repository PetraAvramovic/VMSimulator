package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;

import rs.ac.bg.etf.model.table.PageTable;

public class PageSimulationContext extends SimulationContext
{
    private ArrayList<PageTable> pageTables = new ArrayList<>();
    private long maxPages;

    public PageSimulationContext(SimulationConfig config) 
    {
        super(config);
    }

    public PageTable getPageTable(int user)
    {
        return pageTables.get(user);
    }

    public long getPageComponent()
    {
        long address = getCurrentInstruction().getVirtualAddress();
        int wordBits = getWordBits();
        return address >> wordBits;
    }

    @Override
    public void init()
    {
        super.init();

        maxPages = 1l << config.getPageBits(); 

        for (int i = 0; i < numberOfUsers; i++)
        {
            PageTable table = new PageTable(diskAddressGenerator, i, maxPages);
            table.init(config.getPageTables().get(i));
            pageTables.add(table);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "PageSimulationContext[%s, tlb=%s, maxPages=%d]",
            super.toString(), tlb, maxPages));
        
        if (pageTables != null && !pageTables.isEmpty())
        {
            sb.append(String.format("%n  PageTables (%d):", pageTables.size()));
            for (int i = 0; i < pageTables.size(); i++)
            {
                sb.append(String.format("%n    [%d]: %s", i, pageTables.get(i).toString()));
            }
        }
        
        return sb.toString();
    }
}
