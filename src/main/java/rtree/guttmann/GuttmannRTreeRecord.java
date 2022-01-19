package rtree.guttmann;

import rtree.base.DataObject;
import rtree.base.MBR;
import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;

public class GuttmannRTreeRecord extends RTreeRecord {

    public GuttmannRTreeRecord(MBR mbr, DataObject data){
        super(mbr, data);
    }

    public GuttmannRTreeRecord(MBR mbr, RTreeNode child) {
        super(mbr, child);
    }
}
