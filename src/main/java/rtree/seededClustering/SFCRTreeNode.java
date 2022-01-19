package rtree.seededClustering;

import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;
import java.util.ArrayList;

/**
 * RTree node also containing space-filling curve value. Used as record when bulk-loading.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */

public class SFCRTreeNode extends RTreeNode implements Comparable<SFCRTreeNode> {
    private int lowX = -1;

    public SFCRTreeNode(int id, int height, ArrayList<RTreeRecord> records) {
        super(id, height, records);

        if (!records.isEmpty()) {
            this.lowX = this.getMbr().getLowX();
        }
    }

    public SFCRTreeNode(SFCRTreeNode node) {
        super(node);
        this.lowX = node.getLowX();
    }

    public int getLowX() {
        this.lowX = this.getMbr().getLowX();
        return this.lowX;
    }

    public ArrayList<SFCRTreeRecord> getRecordsAsSFC() {
        ArrayList<SFCRTreeRecord> sfcRecords = new ArrayList<>();
        for(RTreeRecord record : getRecords()) {
            sfcRecords.add((SFCRTreeRecord) record);
        }
        return sfcRecords;
    }

    @Override
    public int compareTo(SFCRTreeNode node) {
        return this.getLowX() - node.getLowX();
    }
}
