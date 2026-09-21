package rs.ac.bg.etf.view.util;

/**
 * The one place the app's responsive-scaling policy lives: which UI scale ({@link UiScale}) a window
 * of a given size gets. All the tuning numbers are here.
 *
 * <p>The UI was designed at 1080p fullscreen, which is scale 1.0. Below that a screen shrinks only as
 * far as needed for its own minimum size to fit (so nothing overflows or overlaps); above it the
 * whole UI grows in proportion to the extra screen. Scales are quantized to {@link #STEP}, so a
 * window being dragged rebuilds the screen only when it crosses a step, not on every pixel.
 */
public final class ResponsiveLayout {
    /** The viewport the UI was designed at (1080p fullscreen) -- shown at scale 1.0. */
    public static final double REFERENCE_WIDTH = 1920;
    public static final double REFERENCE_HEIGHT = 1080;

    /** Legibility floor: below this the window itself is stopped from shrinking further. */
    public static final double MIN_SCALE = 0.6;
    /** Ceiling for growing on very large displays (4K at 100% lands exactly on 1080p's look). */
    public static final double MAX_SCALE = 2.0;
    /**
     * Granularity of the scale; every screen rebuild is triggered by crossing one of these. Fine enough
     * that following a dragged window reads as a continuous change rather than a few jumps (it was
     * 0.05, i.e. 5% at a time); not finer, because each rebuild costs tens of milliseconds and a
     * drag that crosses steps faster than that just spends its time rebuilding. Each distinct scale
     * also gets a generated (and cached) theme stylesheet.
     */
    public static final double STEP = 0.02;

    /**
     * Extra room, as a fraction of a screen's design minimum size, required before a scale is
     * chosen. A screen's real minimum at a rounded scale differs from "design minimum x scale" by a
     * couple of percent (fonts are hinted at whole pixel sizes, so a 13px font at 0.75 is 10px, 2.6%
     * wider than proportional), so this keeps a screen that "exactly" fits from ending up a little
     * too big for its window -- which would cost another rebuild to correct (done before the first
     * frame, see {@code App.adoptWantedScale}, so it costs time, not a visible flash).
     *
     * <p>Also the size of a shrink step when a dragged window stops fitting its screen: the scale
     * drops to leave this much room, so the smaller it is the smaller -- and the more frequent -- the
     * jumps. 0.03 made every shrink a 3-5% jump whatever {@link #STEP} was; 0.015 with a 0.02 step
     * gives ~2% jumps, at the price of more corrective rebuilds.
     */
    public static final double FIT_MARGIN = 0.015;

    /**
     * The same kind of room, for the smallest window the app allows (see {@link #smallestSceneSize}).
     * Kept at the old {@code FIT_MARGIN} of 3%: at the floor scale there is no smaller one to fall
     * back on, so a screen whose real minimum comes out a little above the estimate would be clipped
     * -- and unlike a scale, this margin has no effect on how a drag looks.
     */
    public static final double MIN_WINDOW_MARGIN = 0.03;

    /** Share of the primary screen's usable area the window opens at (before maximizing). */
    public static final double INITIAL_WINDOW_FRACTION = 0.85;

    /** Smallest window any screen may be shrunk to, regardless of its own declared minimum. */
    public static final double MIN_WINDOW_WIDTH = 640;
    public static final double MIN_WINDOW_HEIGHT = 480;

    /**
     * How long after a window is first found to want another scale the screen is rebuilt for it.
     * Short, because until then everything is still drawn at the old scale: a rebuild (every node
     * created and styled again) measures about 60 ms for the workbench once the JVM is warm (see
     * {@code -Dvmsim.timing=true}), so it is worth doing straight away, mid-drag, rather than waiting
     * for the window to stop. What the delay buys is only that the rebuild never runs inside the pulse
     * that noticed the need (so it is about one pulse), and that a burst of resize events costs one
     * rebuild, not several.
     */
    public static final long RESCALE_DELAY_MILLIS = 10;

    /**
     * Least time between the end of one rebuild and the start of the next; 0 = as soon as the window
     * wants another scale. A window dragged across several scale steps is followed one {@link #STEP}
     * at a time -- each jump small -- instead of staying at the old scale until the drag ends and then
     * jumping all the way. A rebuild is ~40 ms, so a drag that keeps crossing steps spends 30-40% of
     * its time rebuilding; raise this (120 was tried) if that is too heavy, at the cost of a coarser,
     * more stepped look.
     */
    public static final long RESCALE_INTERVAL_MILLIS = 0;

    /**
     * How many CSS + layout + re-route rounds a screen that has just been mounted gets before its
     * first frame is drawn (see {@code ResponsiveHost.settleNow}). A round that has nothing left to do
     * costs next to nothing; a screen needs a few because one round's results (a canvas growing to its
     * viewport, a design size changing) are what the next round lays out.
     */
    public static final int SETTLE_PASSES = 4;

    /**
     * Most scale switches made back to back, in one go, when a freshly mounted screen turns out to want
     * another scale (see {@code App.adoptWantedScale}). Normally one; the cap only stops two scales
     * that each look wrong for the other from bouncing forever.
     */
    public static final int MAX_SCALE_SWITCHES = 4;

    private ResponsiveLayout() {
    }

    /** The scale one {@link #STEP} below {@code scale}, or {@code scale} itself if that is the floor. */
    public static double stepBelow(double scale) {
        return Math.max(MIN_SCALE, round(scale - STEP));
    }

    /**
     * The UI scale for a viewport of {@code width} x {@code height} showing a screen whose smallest
     * usable size at scale 1.0 is {@code designMinWidth} x {@code designMinHeight} (0 = no minimum).
     *
     * <p>If the screen's minimum doesn't fit at 1.0 it shrinks until it does (never below {@link
     * #MIN_SCALE}). Otherwise it stays at 1.0 until the viewport outgrows the 1080p reference in both
     * dimensions, after which it grows with the viewport up to {@link #MAX_SCALE}. The result is
     * always rounded <em>down</em> to a multiple of {@link #STEP}, so it never claims more room than
     * there is.
     */
    public static double targetScale(double width, double height, double designMinWidth, double designMinHeight) {
        if (width <= 0 || height <= 0)
            return UiScale.DESIGN_FACTOR;

        double roomFactor = 1 + FIT_MARGIN;
        double fit = Math.min(
                designMinWidth > 0 ? width / (designMinWidth * roomFactor) : Double.POSITIVE_INFINITY,
                designMinHeight > 0 ? height / (designMinHeight * roomFactor) : Double.POSITIVE_INFINITY);

        double raw = fit < 1
                ? fit
                : Math.min(fit, Math.max(1, Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT)));

        // The tiny epsilon keeps an exact multiple (e.g. 0.85 computed as 0.8499999999) on its step.
        double stepped = Math.floor(raw / STEP + 1e-9) * STEP;
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, round(stepped)));
    }

    /**
     * The real (scene) size at which a screen with design minimum {@code designMin} has just been
     * scaled down to {@link #MIN_SCALE} -- i.e. the smallest scene worth allowing.
     */
    public static double smallestSceneSize(double designMin) {
        return designMin * (1 + MIN_WINDOW_MARGIN) * MIN_SCALE;
    }

    // Trims floating-point dust (0.8500000000000001) so equal scales compare equal.
    private static double round(double scale) {
        return Math.round(scale * 1000) / 1000.0;
    }
}
