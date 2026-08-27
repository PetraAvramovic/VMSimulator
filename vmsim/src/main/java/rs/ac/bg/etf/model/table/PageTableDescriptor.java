package rs.ac.bg.etf.model.table;

public class PageTableDescriptor 
{
    private boolean valid;
    private boolean dirty;
    private long block;
    private long disk;
    private long page;

    public PageTableDescriptor(boolean valid, boolean dirty, long block, long disk, long page) {
        this.valid = valid;
        this.dirty = dirty;
        this.block = block;
        this.disk = disk;
        this.page = page;
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
    public long getPage() {
        return page;
    }
    public void setPage(long page) {
        this.page = page;
    }

    @Override
    public String toString()
    {
        return String.format("PageTableDescriptor[valid=%s, dirty=%s, block=%x, disk=%08X, page=%d]",
            valid, dirty, block, disk, page);
    }
}
