package rs.ac.bg.etf.model.disk;

public class DiskAddressGenerator 
{
    private static final long TOTAL_DISK_BLOCKS = 1L << 32;
    private static final long HALF_BITS_MASK = 0xFFFFL; 
    private static final int HALF_BITS_SHIFT = 16;

    private static final long COR_A = 0x456789ABL;
    private static final long COR_B = 0x13579BDFL;

    private final long seed;

    public DiskAddressGenerator(long seed) 
    {
        long mixed = (seed ^ (seed >>> 16)) * 0x85EBCA6BL;
        mixed = (mixed ^ (mixed >>> 13)) * 0xC2B2AE35L;
        this.seed = (mixed ^ (mixed >>> 16)) & 0xFFFFFFFFL;
    }

    // A permutation of [0, TOTAL_DISK_BLOCKS): distinct inputs below that bound never share an address
    // (SimulationConfig.validate() keeps the (user, page) pairs within it).
    public long getDiskAddress(long input) {
        long derivedInput = (input + seed) % TOTAL_DISK_BLOCKS;

        // Balanced 32-bit Feistel cipher execution path
        long left = derivedInput >>> HALF_BITS_SHIFT;
        long right = derivedInput & HALF_BITS_MASK;

        for (int i = 0; i < 4; i++) {
            long roundFunction = ((right * COR_A) ^ COR_B) + i;
            long nextLeft = right;
            long nextRight = (left ^ roundFunction) & HALF_BITS_MASK;
            
            left = nextLeft;
            right = nextRight;
        }

        long finalAddress = (left << HALF_BITS_SHIFT) | right;
        return finalAddress & 0xFFFFFFFFL; 
    }
}
