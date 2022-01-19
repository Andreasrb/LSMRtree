package rtree.base;

/**
 * RTreeRecord is the structure of a record stored in the RTree, equivalent to an entry.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public abstract class RTreeRecord {
    private MBR mbr;
    private DataObject data;
    private RTreeNode child;
    private boolean isLeaf;

    public RTreeRecord(MBR mbr, DataObject data) { // leaf record
        this.mbr = mbr;
        this.data = data;
        this.isLeaf = true;
    }

    public RTreeRecord(MBR mbr, RTreeNode child) { // intermediate record
        this.mbr = mbr;
        this.child = child;
        this.isLeaf = false;
    }

    public MBR getMBR() {
        return mbr;
    }

    public RTreeNode getChild() {
        return this.child;
    }

    public DataObject getData() {
        return this.data;
    }

    public boolean isLeaf() {
        return isLeaf;
    }
}
