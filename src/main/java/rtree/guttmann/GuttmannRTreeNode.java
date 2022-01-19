package rtree.guttmann;

import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;
import java.util.ArrayList;

public class GuttmannRTreeNode extends RTreeNode {

    public GuttmannRTreeNode(int id, int height, ArrayList<RTreeRecord> records) {
        super(id, height, records);
    }

    public GuttmannRTreeNode(GuttmannRTreeNode node) {
        super(node);
    }
}
