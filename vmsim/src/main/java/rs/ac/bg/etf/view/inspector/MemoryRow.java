package rs.ac.bg.etf.view.inspector;

/** One row of the full-memory inspector window: a physical address, its value, and whether it falls in a kernel-locked frame. */
public record MemoryRow(long address, long value, boolean locked) {}
