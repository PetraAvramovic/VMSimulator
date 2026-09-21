package rs.ac.bg.etf.view.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the design-size theme stylesheet for another UI scale: every {@code px} length in every
 * declaration is multiplied by the factor, using the same rounding as {@link UiScale#px(double)}
 * (whole pixels, never rounded away to nothing) -- and font sizes the same as {@link
 * UiScale#font(double)} -- so a length the code computed and the length the CSS draws always agree.
 *
 * <p>Only {@code px} lengths inside declarations are touched. The theme is written in plain px
 * throughout (see the header of light-theme.css); unitless numbers (effect radii, opacities) are
 * left as they are. It works on any theme's stylesheet -- it never looks at colours.
 */
final class ThemeCss {
    // A whole declaration ("-fx-padding: 4px 0;"), so the property name can pick the rounding rule.
    private static final Pattern DECLARATION = Pattern.compile("(-fx-[a-z0-9-]+)(\\s*:)([^;{}]*)(;)");
    private static final Pattern PX_LENGTH = Pattern.compile("(?<![\\w.#-])(-?\\d*\\.?\\d+)px");

    // Stroke widths keep their sub-pixel proportion (a 1.5px wire should stay thinner than a 2px
    // border); rounded to quarter pixels only to keep the text tidy.
    private static final String STROKE_WIDTH = "-fx-stroke-width";
    private static final String FONT_SIZE = "-fx-font-size";
    private static final double STROKE_STEP = 0.25;

    private ThemeCss() {
    }

    static String scale(String designCss, double factor) {
        Matcher declarations = DECLARATION.matcher(designCss);
        StringBuilder out = new StringBuilder(designCss.length());
        while (declarations.find()) {
            String property = declarations.group(1);
            String value = scaleValue(property, declarations.group(3), factor);
            declarations.appendReplacement(out,
                    Matcher.quoteReplacement(property + declarations.group(2) + value + declarations.group(4)));
        }
        declarations.appendTail(out);
        return out.toString();
    }

    private static String scaleValue(String property, String value, double factor) {
        Matcher lengths = PX_LENGTH.matcher(value);
        StringBuilder out = new StringBuilder(value.length());
        while (lengths.find()) {
            double design = Double.parseDouble(lengths.group(1));
            double scaled;
            if (FONT_SIZE.equals(property))
                scaled = UiScale.fontSize(design, factor);
            else if (STROKE_WIDTH.equals(property))
                scaled = Math.max(STROKE_STEP, Math.round(design * factor / STROKE_STEP) * STROKE_STEP);
            else
                scaled = UiScale.length(design, factor);
            lengths.appendReplacement(out, Matcher.quoteReplacement(format(scaled) + "px"));
        }
        lengths.appendTail(out);
        return out.toString();
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
