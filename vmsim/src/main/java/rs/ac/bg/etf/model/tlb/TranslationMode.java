package rs.ac.bg.etf.model.tlb;

/**
 * Identifies which address translation scheme is being simulated.
 * Determines the meaning of the TLB's generic "addressComponent"
 * (page number, segment number, or a combined segment+page value).
 */
public enum TranslationMode
{
    PAGE,
    SEGMENT,
    PAGE_SEGMENT
}
