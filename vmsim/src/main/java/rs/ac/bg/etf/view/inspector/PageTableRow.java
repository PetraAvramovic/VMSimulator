package rs.ac.bg.etf.view.inspector;

/** One page table entry, as shown by the full-table inspector ({@link PageTableInspectorWindow}). */
public record PageTableRow(long page, boolean valid, boolean dirty, long block, long disk) {}
