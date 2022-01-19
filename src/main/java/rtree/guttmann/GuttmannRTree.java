package rtree.guttmann;

import rtree.base.*;
import java.util.ArrayList;

public class GuttmannRTree extends RTreeStructure {
    private int recordCount;

    public GuttmannRTree(int dimensions, int m, int M) {
        super(dimensions, m, M);

        createEmtpyRoot();
        this.recordCount = 0;
    }

    private void createEmtpyRoot() {
        RTreeNode root = new GuttmannRTreeNode(createNodeId(), 0, new ArrayList<>());
        setRoot(root);
    }

    public void insertData(ArrayList<DataObject> data) {
        for (DataObject dataObject : data) {float[] low = {dataObject.getLowX(), dataObject.getLowY()};
            float[] high = {dataObject.getHighX(), dataObject.getHighY()};
            MBR mbr = new MBR(low, high);

            GuttmannRTreeRecord record = new GuttmannRTreeRecord(mbr, dataObject);
            insert(record);
            this.recordCount ++;
        }
    }

    public int getRecordCount(){
        return this.recordCount;
    }

    @Override
    protected GuttmannRTreeNode createNodeWithoutRecords(int height) {
        return new GuttmannRTreeNode(createNodeId(), height, new ArrayList<>());
    }

    @Override
    protected void createNewRootNode(ArrayList<RTreeNode> nodes) {
        ArrayList<RTreeRecord> records = new ArrayList<>();
        for (RTreeNode node : nodes) {
            GuttmannRTreeRecord record = new GuttmannRTreeRecord(node.getMbr(), node);
            records.add(record);
        }

        RTreeNode root = new GuttmannRTreeNode(createNodeId(), getHeight() + 1, records);
        setRoot(root);
    }

    @Override
    protected RTreeRecord createRecord(RTreeNode node) {
        return new GuttmannRTreeRecord(node.getMbr(), node);
    }

    @Override
    public RTreeNode createNodeFromExistingNode(RTreeNode insertionNode) {
        GuttmannRTreeNode castedNode = (GuttmannRTreeNode) insertionNode;
        return new GuttmannRTreeNode(castedNode);
    }
}
