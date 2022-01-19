package rtree.seededClustering;

import java.util.ArrayList;
import java.util.Collections;

/**
 * RTree created by bulk-loading (packing method of RTree, ordering done by Z-value
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu>
 */
public class SmallRTree extends SFCRTreeStructure {
    private int nodeIdOfSeed;
    private int heightOfSeedNode;

    public SmallRTree(int dimensions, int m, int M, ArrayList<SFCRTreeRecord> records, int nodeIdOfSeed, int heightOfSeedNode) {
        super(dimensions, m, M, records.size());
        this.nodeIdOfSeed = nodeIdOfSeed;
        this.heightOfSeedNode = heightOfSeedNode;
        bulkLoad(records);
    }

    /**
     * method for constructing a RTree from scratch based on leaf records
     * @param records: leaf records, list of all leaf objects to put in tree
     */
    public void bulkLoad(ArrayList<SFCRTreeRecord> records) {
        int height = 0;
        ArrayList<SFCRTreeNode> nodes;

        while(true) {
            Collections.sort(records);
            nodes = createNodesInBulk(records, height);

            if (nodes.size() == 1) {
                setRoot(nodes.get(0));
                break;
            }
            height ++;
            records = createRecordsForNodes(nodes);
        }
    }

    public int getNodeIdOfSeed() {
        return nodeIdOfSeed;
    }

    public int getHeightOfSeedNode() {
        return heightOfSeedNode;
    }
}
