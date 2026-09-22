package rs.ac.bg.etf.view.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import rs.ac.bg.etf.model.settings.AppTheme;

/**
 * The app's one UI scale factor, and everything that turns a design-size length into a real one.
 *
 * <p>Every length in the views/CSS is written at the 1080p design size. To fit other windows and
 * displays the UI is not drawn through a scale <em>transform</em> -- a transformed scene loses hinted
 * text and lands borders between device pixels, which is visibly soft -- but is instead laid out
 * again at a different size: fonts, paddings, borders and every code-side geometry constant are all
 * multiplied by {@link #factor()} and rounded to whole pixels, so text is rendered at its real size
 * and stays crisp at every scale.
 *
 * <ul>
 *   <li>Code passes each design length through {@link #px(double)} (and font sizes through
 *       {@link #font(double)}) <em>when it builds a view</em>. The factor only changes between
 *       builds: when it does, {@code App} rebuilds the current screen, so a view can treat it as a
 *       constant for its own lifetime.</li>
 *   <li>The CSS is the current {@link AppTheme}'s design-size file, rewritten for the factor by
 *       {@link ThemeCss} and loaded through {@link #applyTheme(Scene)}; the code and the CSS use the
 *       same rounding rules, so a font measured in code is the font the stylesheet draws. Every
 *       theme's file has the same sizes (only colours differ), so switching theme never moves
 *       anything and needs no rebuild: {@link #changeTheme(AppTheme, Collection)} restyling the
 *       open scenes is the whole change.</li>
 * </ul>
 *
 * <p>At exactly 1.0 nothing is rounded or rewritten: the design values pass through untouched and
 * the original stylesheet is loaded as-is.
 */
public final class UiScale {
    private static final String RESOURCE_DIR = "/rs/ac/bg/etf/";
    private static final String FONTS_RESOURCE = RESOURCE_DIR + "fonts.css";

    /** The factor the design lengths are written at. */
    public static final double DESIGN_FACTOR = 1.0;

    /**
     * Smallest font size a scaled font is allowed to shrink to (a design font already smaller than
     * this is left alone). Geometry keeps shrinking in proportion; text stops here so it stays
     * legible.
     */
    private static final double MIN_FONT_PX = 8;

    // Where a scene remembers the factor it was styled at (see changeTheme).
    private static final String SCENE_FACTOR_KEY = "vmsim.uiScale";

    private static final ReadOnlyDoubleWrapper factor = new ReadOnlyDoubleWrapper(DESIGN_FACTOR);
    // The factor everything is built at while inside atScale(), NaN outside it (FX thread only).
    private static double scopedFactor = Double.NaN;
    // Generated theme stylesheets, per theme and then keyed by factor in thousandths (0.85 -> 850).
    private static final Map<AppTheme, Map<Long, String>> themeUrls = new EnumMap<>(AppTheme.class);
    private static Path themeDirectory;
    private static AppTheme theme = AppTheme.LIGHT;

    private UiScale() {
    }

    public static double factor() {
        return factor.get();
    }

    /** The theme every scene should currently be carrying (see {@link #applyTheme(Scene)}). */
    public static AppTheme theme() {
        return theme;
    }

    /**
     * Sets the theme that scenes are styled with from now on, before any scene has been styled. To
     * change it while windows are showing, use {@link #changeTheme(AppTheme, Collection)}.
     */
    public static void setTheme(AppTheme newTheme) {
        theme = newTheme;
    }

    public static ReadOnlyDoubleProperty factorProperty() {
        return factor.getReadOnlyProperty();
    }

    /**
     * Switches the factor. The caller must have made the matching theme available first
     * ({@link #prepareTheme(double)}) and is responsible for rebuilding whatever was built at the
     * old factor.
     */
    public static void setFactor(double newFactor) {
        factor.set(newFactor);
    }

    /**
     * Runs {@code build} with every length, font size and stylesheet it asks {@code UiScale} for
     * resolved at {@code atFactor} instead of the app's current factor. For a window that is not part
     * of the main screen and so must not follow it (see {@link InspectorWindows}): everything the
     * window is built from -- {@link #px}, {@link #font}, {@link WidthCalculator}, {@link
     * #applyTheme(Scene)} -- reads the same factor, and its scene remembers it, so a later theme
     * change restyles it at that same scale. {@code atFactor} must be {@link #DESIGN_FACTOR} or the
     * current factor, the only ones whose stylesheets are guaranteed to have been generated.
     */
    public static <T> T atScale(double atFactor, Supplier<T> build) {
        double outer = scopedFactor;
        scopedFactor = atFactor;
        try {
            return build.get();
        } finally {
            scopedFactor = outer;
        }
    }

    // The factor lengths, fonts and stylesheets are resolved at right now: the app's, unless inside atScale().
    private static double effectiveFactor() {
        return Double.isNaN(scopedFactor) ? factor.get() : scopedFactor;
    }

    /** A design-size length at the current factor, in whole pixels (never rounded away to zero). */
    public static double px(double designLength) {
        return length(designLength, effectiveFactor());
    }

    /** A design-size font size at the current factor (see {@link #MIN_FONT_PX}). */
    public static double font(double designSize) {
        return fontSize(designSize, effectiveFactor());
    }

    public static Insets insets(double designAll) {
        return new Insets(px(designAll));
    }

    public static Insets insets(double designTop, double designRight, double designBottom, double designLeft) {
        return new Insets(px(designTop), px(designRight), px(designBottom), px(designLeft));
    }

    // Shared with ThemeCss so the code and the stylesheet round identically.
    static double length(double designLength, double atFactor) {
        if (atFactor == DESIGN_FACTOR || designLength == 0)
            return designLength;
        double scaled = Math.round(designLength * atFactor);
        return scaled == 0 ? Math.signum(designLength) : scaled;
    }

    static double fontSize(double designSize, double atFactor) {
        if (atFactor == DESIGN_FACTOR)
            return designSize;
        return Math.max(Math.min(designSize, MIN_FONT_PX), Math.round(designSize * atFactor));
    }

    // ------------------------------------------------------------------------------------
    // Stylesheets

    /** The stylesheets every scene must carry, for the current theme and factor: the fonts, then the theme. */
    public static List<String> stylesheets() {
        return stylesheets(theme, effectiveFactor());
    }

    private static List<String> stylesheets(AppTheme forTheme, double atFactor) {
        return List.of(resourceUrl(FONTS_RESOURCE), themeUrl(forTheme, atFactor));
    }

    /** Puts the current theme's and factor's stylesheets on {@code scene}, replacing any it had. */
    public static void applyTheme(Scene scene) {
        style(scene, theme, effectiveFactor());
    }

    /**
     * Switches to {@code newTheme} and restyles every scene in {@code scenes} with it. Themes only
     * differ in colours, so nothing is rebuilt. Each scene keeps the scale it was styled at: only
     * the main window follows the current factor (it is rebuilt when that changes), while an
     * inspector or editor window keeps the size it was built at until it is closed.
     *
     * @return false, changing nothing, if a stylesheet could not be generated
     */
    public static boolean changeTheme(AppTheme newTheme, Collection<Scene> scenes) {
        // Everything is generated first so a failure leaves the old theme fully in place.
        if (!prepareTheme(newTheme, factor.get()))
            return false;
        for (Scene scene : scenes)
            if (!prepareTheme(newTheme, factorOf(scene)))
                return false;

        theme = newTheme;
        for (Scene scene : scenes)
            style(scene, newTheme, factorOf(scene));
        return true;
    }

    // The scale a scene was last styled at (a scene never styled by us counts as the current one).
    private static double factorOf(Scene scene) {
        return scene.getProperties().get(SCENE_FACTOR_KEY) instanceof Double styledAt ? styledAt : factor.get();
    }

    private static void style(Scene scene, AppTheme forTheme, double atFactor) {
        scene.getProperties().put(SCENE_FACTOR_KEY, atFactor);
        List<String> wanted = stylesheets(forTheme, atFactor);
        ObservableList<String> current = scene.getStylesheets();
        if (current.size() != wanted.size()) {
            current.setAll(wanted);
            return;
        }
        // Same stylesheets, so only the entries that differ are swapped -- on a scale or theme change
        // that is just the theme. setAll would take fonts.css out and put it back too, and every time that
        // stylesheet is loaded the StyleManager calls Font.loadFont for each of its @font-face fonts
        // (which copies the file out and registers it again), a real cost on every rebuild.
        for (int i = 0; i < wanted.size(); i++)
            if (!current.get(i).equals(wanted.get(i)))
                current.set(i, wanted.get(i));
    }

    /** {@link #prepareTheme(AppTheme, double)} for the current theme, ahead of a scale change. */
    public static boolean prepareTheme(double atFactor) {
        return prepareTheme(theme, atFactor);
    }

    /**
     * Generates (once) the stylesheet of {@code forTheme} for {@code atFactor}.
     *
     * @return false if it could not be written, in which case the factor (or theme) must not be
     *         switched to (the code would scale but the CSS wouldn't)
     */
    public static synchronized boolean prepareTheme(AppTheme forTheme, double atFactor) {
        Map<Long, String> generated = themeUrls.computeIfAbsent(forTheme, ignored -> new HashMap<>());
        if (atFactor == DESIGN_FACTOR || generated.containsKey(key(atFactor)))
            return true;
        try {
            String designCss;
            try (InputStream in = UiScale.class.getResourceAsStream(themeResource(forTheme))) {
                if (in == null)
                    return false;
                designCss = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (themeDirectory == null) {
                themeDirectory = Files.createTempDirectory("vmsim-theme-");
                themeDirectory.toFile().deleteOnExit();
            }
            Path file = themeDirectory.resolve(forTheme.name().toLowerCase(Locale.ROOT) + "-" + key(atFactor) + ".css");
            Files.writeString(file, ThemeCss.scale(designCss, atFactor), StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            generated.put(key(atFactor), file.toUri().toString());
            return true;
        } catch (IOException | RuntimeException e) {
            // Caller (switchScale) treats false as "stay at the current scale/theme".
            return false;
        }
    }

    private static synchronized String themeUrl(AppTheme forTheme, double atFactor) {
        if (atFactor == DESIGN_FACTOR)
            return resourceUrl(themeResource(forTheme));
        String url = themeUrls.getOrDefault(forTheme, Map.of()).get(key(atFactor));
        // prepareTheme() is a precondition of setFactor()/setTheme(); fall back to the theme's design
        // stylesheet rather than to an unstyled scene if that was ever skipped.
        return url != null ? url : resourceUrl(themeResource(forTheme));
    }

    private static String themeResource(AppTheme forTheme) {
        return RESOURCE_DIR + forTheme.stylesheetName();
    }

    private static String resourceUrl(String resource) {
        return UiScale.class.getResource(resource).toExternalForm();
    }

    private static long key(double atFactor) {
        return Math.round(atFactor * 1000);
    }
}
