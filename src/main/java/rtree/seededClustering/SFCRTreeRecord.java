package rtree.seededClustering;

import rtree.base.DataObject;
import rtree.base.MBR;
import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;

/**
 * RTree record also containing space-filling curve value. Used as record when bulk-loading.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class SFCRTreeRecord extends RTreeRecord implements Comparable<SFCRTreeRecord> {

    private int lowX; // TODO: this need to change to correct value, not sure if int or double or ?

    public SFCRTreeRecord(MBR mbr, DataObject data) {
        super(mbr, data);
        this.lowX = mbr.getLowX();
    }

    public SFCRTreeRecord(MBR mbr, RTreeNode child, int lowX) {
        super(mbr, child);
        this.lowX = lowX;
    }

    public int getlowX() {
        return this.lowX;
    }

    @Override
    public int compareTo(SFCRTreeRecord record) {
        return this.getlowX() - record.getlowX();
    }
}
