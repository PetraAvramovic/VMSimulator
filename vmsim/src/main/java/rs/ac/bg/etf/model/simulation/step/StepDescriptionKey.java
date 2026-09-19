package rs.ac.bg.etf.model.simulation.step;

/**
 * One constant per distinct step-description message template. Paired with positional
 * arguments in a {@link StepDescription}; the actual, locale-specific wording lives in a
 * view-layer resource bundle keyed by this enum's {@link #name()} -- this enum and
 * {@link StepDescription} are plain data, deliberately unaware of Locale/ResourceBundle.
 */
public enum StepDescriptionKey
{
    INSTRUCTION_FETCHED,

    TLB_LOOKUP_HIT,
    TLB_LOOKUP_MISS,

    PHYSICAL_ADDRESS_FROM_TLB,

    TLB_INSERTED,

    TLB_EVICTED,
    TLB_EVICTED_DIRTY,
    TLB_REPLACED,
    TLB_REPLACED_DIRTY,

    TLB_DIRTY_BIT_UPDATED,

    MEMORY_READ,
    MEMORY_WRITTEN,
    MEMORY_EXECUTED,

    PAGE_TABLE_ADDRESS_FORMED,
    PHYSICAL_ADDRESS_FROM_PAGE_TABLE,

    PAGE_TABLE_LOOKUP_FAULT,
    PAGE_TABLE_LOOKUP_HIT,
    PAGE_TABLE_LOOKUP_HIT_DIRTY,

    PAGE_FAULT_NO_FRAME,
    PAGE_FAULT_LOADING,

    FRAME_EVICTED,
    FRAME_EVICTED_DIRTY,

    PAGE_LOADED_INTO_MEMORY,
    PAGE_STORED_TO_DISK,

    PAGE_TABLE_DIRTY_BIT_UPDATED
}
