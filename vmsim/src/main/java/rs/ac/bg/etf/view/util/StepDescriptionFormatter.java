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

        // An argument that is itself a StepDescription is a phrase (e.g. "TLB entry 1 (set 0)") to be
        // worded in place, by the same rules.
        Object[] args = description.args().clone();
        for (int i = 0; i < args.length; i++)
            if (args[i] instanceof StepDescription phrase)
                args[i] = format(phrase, locale);

        return String.format(locale, pattern, args);
    }

    public static String format(StepDescription description)
    {
        return format(description, Locale.getDefault());
    }
}
