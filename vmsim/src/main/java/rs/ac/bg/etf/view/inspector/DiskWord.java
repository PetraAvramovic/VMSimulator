package rs.ac.bg.etf.view.inspector;

/** One row of the disk block inspector window: a word offset within the block and its value (0 if
 *  nothing has ever written there -- disk blocks are sparse, so most offsets read this way). */
public record DiskWord(long offset, long value) {}
