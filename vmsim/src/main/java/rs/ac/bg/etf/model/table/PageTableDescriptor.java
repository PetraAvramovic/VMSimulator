package rs.ac.bg.etf.model.table;

public class PageTableDescriptor 
{
    private boolean valid;
    private boolean dirty;
    private long block;
    private long disk;

    public PageTableDescriptor(boolean valid, boolean dirty, long block, long disk) {
        this.valid = valid;
        this.dirty = dirty;
        this.block = block;
        this.disk = disk;
    }

    public boolean isValid() {
        return valid;
    }
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    public boolean isDirty() {
        return dirty;
    }
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    public long getBlock() {
        return block;
    }
    public void setBlock(long block) {
        this.block = block;
    }
    public long getDisk() {
        return disk;
    }
    public void setDisk(long disk) {
        this.disk = disk;
    }

    @Override
    public String toString()
    {
        return String.format("PageTableDescriptor[valid=%s, dirty=%s, block=%x, disk=%08X]",
            valid, dirty, block, disk);
    }
}
