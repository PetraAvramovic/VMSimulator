package rs.ac.bg.etf.view.util;

import javafx.scene.control.ScrollBar;

/**
 * Configures a {@link ScrollBar} that drives a windowed table's {@code windowStart} over a
 * potentially huge entry range (physical frames, page table entries, ...), while keeping the
 * rendered thumb within a fixed, always-usable fraction of the track.
 *
 * <p>{@code ScrollBar}'s own thumb-length formula is {@code visibleAmount / ((max - min) +
 * visibleAmount)}. Feeding it the literal {@code rowsShown}/{@code entryCount} ratio -- as in
 * {@code setMax(entryCount - rowsShown); setVisibleAmount(rowsShown)} -- means the thumb swells to
 * fill almost the entire track when the entry count is only slightly larger than what's visible,
 * and shrinks to an invisible sliver once the entry count is in the billions. Instead, {@code min}/
 * {@code max}/{@code visibleAmount} are set over a fixed virtual range so the thumb ratio is always
 * clamped to {@code [MIN_THUMB_RATIO, MAX_THUMB_RATIO]}; {@link #toWindowStart} and
 * {@link #syncValue} translate the bar's virtual value to and from the real {@code windowStart}.
 */
public final class AddressScaleScrollBar
{
    private static final double VIRTUAL_RANGE = 1_000_000;
    private static final double MIN_THUMB_RATIO = 0.05;
    private static final double MAX_THUMB_RATIO = 0.4;

    private AddressScaleScrollBar()
    {
    }

    /** Sets {@code bar}'s min/max/visibleAmount/increments for {@code rowsShown} rows visible out of {@code entryCount}. */
    public static void configure(ScrollBar bar, long entryCount, int rowsShown)
    {
        long realMax = realMax(entryCount, rowsShown);
        double ratio = realMax == 0 ? MAX_THUMB_RATIO
                : clamp((double) rowsShown / entryCount, MIN_THUMB_RATIO, MAX_THUMB_RATIO);
        double visibleAmount = ratio * VIRTUAL_RANGE / (1 - ratio);

        bar.setMin(0);
        bar.setMax(VIRTUAL_RANGE);
        bar.setVisibleAmount(visibleAmount);
        bar.setBlockIncrement(realMax == 0 ? VIRTUAL_RANGE : VIRTUAL_RANGE * rowsShown / realMax);
        bar.setUnitIncrement(realMax == 0 ? VIRTUAL_RANGE : VIRTUAL_RANGE / realMax);
    }

    /** The real window-start position {@code bar}'s current (virtual-range) value maps to. */
    public static long toWindowStart(ScrollBar bar, long entryCount, int rowsShown)
    {
        long realMax = realMax(entryCount, rowsShown);
        return realMax == 0 ? 0 : Math.round(bar.getValue() / VIRTUAL_RANGE * realMax);
    }

    /** Sets {@code bar}'s (virtual-range) value to reflect the real window-start position {@code windowStart}. */
    public static void syncValue(ScrollBar bar, long windowStart, long entryCount, int rowsShown)
    {
        long realMax = realMax(entryCount, rowsShown);
        double clampedStart = Math.max(0, Math.min(windowStart, realMax));
        bar.setValue(realMax == 0 ? 0 : clampedStart / (double) realMax * VIRTUAL_RANGE);
    }

    private static long realMax(long entryCount, int rowsShown)
    {
        return Math.max(0, entryCount - rowsShown);
    }

    private static double clamp(double v, double min, double max)
    {
        return Math.max(min, Math.min(max, v));
    }
}
