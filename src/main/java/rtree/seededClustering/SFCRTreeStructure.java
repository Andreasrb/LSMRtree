package rtree.seededClustering;

import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;
import rtree.base.RTreeStructure;
import java.util.ArrayList;
import java.util.List;

/**
 * RTree structure also containing space-filling curve value. Used for R-tree components in LSM-tree levels
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */

public abstract class SFCRTreeStructure extends RTreeStructure {
    private int recordCount;

    public SFCRTreeStructure(int dimensions, int m, int M, int recordCount) {
        super(dimensions, m, M);
        this.recordCount = recordCount;
    }

    public SFCRTreeStructure(int dimensions, int m, int M, RTreeNode root) {
        super(dimensions, m, M, root);
    }

    protected ArrayList<SFCRTreeRecord> createRecordsForNodes(ArrayList<SFCRTreeNode> nodes) {
        ArrayList<SFCRTreeRecord> records = new ArrayList<>();
        for (SFCRTreeNode node : nodes) {
            SFCRTreeRecord record = (SFCRTreeRecord) createRecord(node);
            records.add(record);
        }
        return records;
    }

    protected ArrayList<SFCRTreeNode> createNodesInBulk(ArrayList<SFCRTreeRecord> records, int height) {
        ArrayList<SFCRTreeNode> nodes = new ArrayList<>();

        int numberOfNodes = (int) Math.ceil((double) records.size() / this.getM());
        int lastNodeSize = records.size()%this.getM();
        int numberOfNodesCreated = 0;

        if (numberOfNodes == 1){
            ArrayList<RTreeRecord> childRecords = new ArrayList<>(records);
            SFCRTreeNode node = new SFCRTreeNode(createNodeId(), height, childRecords);
            nodes.add(node);


            return nodes;
        }

        for (int i = 0; i < records.size(); i += this.getM()) {
            ArrayList<RTreeRecord> childRecords = new ArrayList<>();

            if (numberOfNodesCreated == numberOfNodes - 2) {
                if (lastNodeSize < this.getm() && lastNodeSize != 0) {
                    List<SFCRTreeRecord> remainingRecords = records.subList(i, records.size());

                    childRecords.addAll(remainingRecords.subList(0, (int) Math.ceil((double)remainingRecords.size()/2)));
                    SFCRTreeNode node = new SFCRTreeNode(createNodeId(), height, childRecords);
                    nodes.add(node);

                    childRecords.clear();

                    childRecords.addAll(remainingRecords.subList((int) Math.ceil((double)remainingRecords.size()/2), remainingRecords.size()));
                    node = new SFCRTreeNode(createNodeId(), height, childRecords);
                    nodes.add(node);
                    break;
                }
                else {
                    childRecords.addAll(records.subList(i, i+this.getM()));
                    SFCRTreeNode node = new SFCRTreeNode(createNodeId(), height, childRecords);
                    nodes.add(node);

                    childRecords.clear();

                    childRecords.addAll(records.subList(i+this.getM(), records.size()));
                    node = new SFCRTreeNode(createNodeId(), height, childRecords);
                    nodes.add(node);
                    break;
                }
            }
            else {
                childRecords.addAll(records.subList(i, i+this.getM()));
                SFCRTreeNode node = new SFCRTreeNode(createNodeId(), height, childRecords);
                nodes.add(node);

                numberOfNodesCreated ++;
            }
        }
        return nodes;
    }

    public void incrementRecordCount() {
        this.recordCount++;
    }

    public int getRecordCount() {
        return this.recordCount;
    }

    public void addToRecordCount(int amount) {
        this.recordCount += amount;
    }

    @Override
    protected void createNewRootNode(ArrayList<RTreeNode> nodes) {
        int height = this.getHeight();
        ArrayList<SFCRTreeNode> newNodes = new ArrayList<>();
        for (RTreeNode sfcNode : nodes) {
            newNodes.add((SFCRTreeNode) sfcNode);
        }

        while (newNodes.size() != 1 ) {
            height ++;
            ArrayList<SFCRTreeRecord> recordsToAdd = createRecordsForNodes(new ArrayList<>(newNodes));
            newNodes = createNodesInBulk(recordsToAdd, height);
        }

        SFCRTreeNode root = newNodes.get(0);
        setRoot(root);
    }

    @Override
    protected RTreeNode createNodeWithoutRecords(int height) {
        return new SFCRTreeNode(createNodeId(), height, new ArrayList<>());
    }

    @Override
    protected RTreeRecord createRecord(RTreeNode node) {
        SFCRTreeNode castedNode = (SFCRTreeNode) node;
        return new SFCRTreeRecord(castedNode.getMbr(), castedNode, castedNode.getLowX());
    }

    @Override
    public RTreeNode createNodeFromExistingNode(RTreeNode insertionNode) {
        SFCRTreeNode castedNode = new SFCRTreeNode((SFCRTreeNode) insertionNode);
        return new SFCRTreeNode(castedNode);
    }
}
