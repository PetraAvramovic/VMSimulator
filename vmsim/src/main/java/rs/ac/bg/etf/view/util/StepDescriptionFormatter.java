package rs.ac.bg.etf.view.util;

import java.util.Locale;
import java.util.ResourceBundle;

import rs.ac.bg.etf.model.simulation.step.StepDescription;

/**
 * Turns a locale-free {@link StepDescription} into displayable text. This is the only place
 * that knows about Locale/ResourceBundle for step descriptions -- the model builds
 * StepDescription values without any awareness of language.
 */
public final class StepDescriptionFormatter
{
    private static final String BUNDLE_BASE_NAME = "rs.ac.bg.etf.i18n.StepDescriptions";

    private StepDescriptionFormatter() { }

    public static String format(StepDescription description, Locale locale)
    {
        if (description == null)
            return "";

        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
        String pattern = bundle.getString(description.key().name());
        return String.format(locale, pattern, description.args());
    }

    public static String format(StepDescription description)
    {
        return format(description, Locale.getDefault());
    }
}
