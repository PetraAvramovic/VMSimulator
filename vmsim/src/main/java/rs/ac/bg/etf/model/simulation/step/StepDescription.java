package rs.ac.bg.etf.model.simulation.step;

/**
 * A step's outcome, in a locale-free form: a message key plus the positional values that
 * fill it in. The model builds these; only the view layer (see the view.util formatter) knows
 * how to turn one into displayable text for a given Locale.
 */
public record StepDescription(StepDescriptionKey key, Object... args)
{
}
