package rs.ac.bg.etf.view.util;

public class ValueConverter 
{
    public static String toHex(long value, int digits) {
        return String.format("0x%0" + digits + "X", value);
    }

    public static int hexDigitsFor(int bits) 
    {
        return Math.max(1, Math.ceilDiv(bits, 4));
    }
}
