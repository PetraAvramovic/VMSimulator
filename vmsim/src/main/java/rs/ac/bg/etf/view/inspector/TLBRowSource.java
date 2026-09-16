package rs.ac.bg.etf.view.inspector;

import java.util.List;

import rs.ac.bg.etf.model.tlb.SetAssociativeTLB;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;
import rs.ac.bg.etf.view.util.WindowedRowSource;

/**
 * Adapts one way's slice of a {@link TLB} to the generic {@code WindowedTableView}: row
 * {@code i} is set {@code i}'s entry in {@code way} for a set-associative TLB, or slot {@code i}
 * directly (way ignored) otherwise. Unlike a page table, a TLB never grows past its configured
 * size -- {@link TLB#getEntries()} already holds every slot (empty ones as {@code null}) -- so
 * there's no on-demand entry to create, just a read.
 */
public class TLBRowSource implements WindowedRowSource<TLBRow>
{
    private final TLB tlb;
    private final SetAssociativeTLB setAssociativeTlb; // non-null iff set-associative
    private final int way;

    public TLBRowSource(TLB tlb, int way)
    {
        this.tlb = tlb;
        this.setAssociativeTlb = tlb instanceof SetAssociativeTLB sat ? sat : null;
        this.way = way;
    }

    @Override
    public long getEntryCount()
    {
        return setAssociativeTlb != null ? setAssociativeTlb.getNumSets() : tlb.getSize();
    }

    @Override
    public TLBRow rowAt(long index)
    {
        int i = (int) index;
        int slot = setAssociativeTlb != null ? setAssociativeTlb.slotFor(i, way) : i;
        List<TLBEntry> entries = tlb.getEntries();
        TLBEntry entry = entries.get(slot);
        return entry == null
                ? new TLBRow(i, false, false, 0, 0)
                : new TLBRow(i, entry.isValid(), entry.isDirty(), entry.getTag(), entry.getBlock());
    }
}
