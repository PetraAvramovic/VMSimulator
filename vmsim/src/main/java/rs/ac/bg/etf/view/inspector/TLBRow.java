package rs.ac.bg.etf.view.inspector;

/** One TLB slot (or, for set-associative, one set within a way), as shown by the full-table
 *  inspector ({@link TLBInspectorWindow}). */
public record TLBRow(int index, boolean valid, boolean dirty, long tag, long block) {}
