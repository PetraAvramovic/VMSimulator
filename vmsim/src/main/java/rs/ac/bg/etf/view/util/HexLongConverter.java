package rs.ac.bg.etf.view.util;

import javafx.util.StringConverter;

/**
 * Displays a {@code Long} as an uppercase hex literal ("0x1A2B") and parses one back, matching how
 * addresses/values already read everywhere else in the app ({@link ValueConverter#toHex}). Also
 * accepts a plain decimal string on input (via {@link Long#decode}), so pasting a value copied from
 * elsewhere in the UI still works. Unparsable/blank input round-trips to 0 rather than throwing --
 * these converters back editable TableView cells, where a thrown exception would leave the cell
 * stuck in an inconsistent edit state.
 */
public class HexLongConverter extends StringConverter<Long>
{
    @Override
    public String toString(Long value)
    {
        return value == null ? "0x0" : String.format("0x%X", value);
    }

    @Override
    public Long fromString(String text)
    {
        if (text == null)
            return 0L;
        String trimmed = text.strip();
        if (trimmed.isEmpty())
            return 0L;
        try {
            return Long.decode(trimmed);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
