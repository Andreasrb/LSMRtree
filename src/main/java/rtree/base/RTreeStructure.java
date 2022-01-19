package rtree.base;

import rtree.guttmann.GuttmannRTree;
import rtree.guttmann.GuttmannRTreeNode;
import rtree.guttmann.GuttmannRTreeRecord;
import rtree.seededClustering.SFCRTreeNode;
import rtree.seededClustering.SFCRTreeRecord;
import rtree.seededClustering.SFCRTreeStructure;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * RTree abstraction. Responsible for the structure of the RTree index, nodes and records.
 * Can be utilized by different variations for bulk-loading, bulk-insertion and insertion one-by-one.
 *
 * Based on Rtree introduced by Guttman
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public abstract class RTreeStructure {

    private final int dimensions;
    private final int m; //m <= M/2
    private final int M;
    private int height;
    private static int nodeCounter = 0;
    private RTreeNode root;
    private int splitCount = 0;

    public RTreeStructure(int dimensions, int m, int M){
        this.dimensions = dimensions;
        this.m = m;
        this.M = M;
    }

    public RTreeStructure(int dimensions, int m, int M, RTreeNode root) {
        this.dimensions = dimensions;
        this.m = m;
        this.M = M;
        setRoot(root);
    }

    // need this test to be able to run tests at the same time
    public void restartCounter() {
        nodeCounter = 0;
    }

    public int createNodeId() {
        nodeCounter++;
        return nodeCounter;
    }

    public void setRoot(RTreeNode root){
        this.root = createNodeFromExistingNode(root);
        this.height = root.getHeight();
    }

    protected abstract void createNewRootNode(ArrayList<RTreeNode> nodes);

    public RTreeNode getRoot() {
        return this.root;
    }

    public int getM() {
        return this.M;
    }

    public int getm() {
        return this.m;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getDimensions() {
        return this.dimensions;
    }

    /**
     * inserts one record into existing RTree. (one-by-one approach, based on insertion by Guttman)
     * finds placement that gives the least area enlargement, starting at root, moving top-down
     */
    public void insert(RTreeRecord record) {
        RTreeNode insertionNode;
        ArrayDeque<RTreeNode> path = new ArrayDeque<>();

        if (this.getHeight() == 0) {
            insertionNode = root;
        }
        else {
            path = findInsertionPath(record, root, path);
            insertionNode = path.pop();
        }

        RTreeNode insertionNodeBefore = createNodeFromExistingNode(insertionNode);
        insertionNode.addRecordAndReadjustMBR(record);

        if (this instanceof SFCRTreeStructure) {
            insertionNode = new SFCRTreeNode(insertionNodeBefore.getId(), insertionNode.getHeight(), insertionNode.getRecords());
        }

        if (insertionNode.getRecordCount() > M) {
            performQuadraticSplitAndReadjustPath(insertionNode, path);
        }
        else {
            adjustMBRsOnPath(insertionNode, insertionNodeBefore, path);
        }
    }

    /** recursive method that finds the node to insert into which creates the least area enlargement
     * returns path of nodes to follow, head of queue contains insertion node
     **/
    private ArrayDeque<RTreeNode> findInsertionPath(RTreeRecord record, RTreeNode node, ArrayDeque<RTreeNode> path) {
        double minAreaEnlargement = node.getRecords().get(0).getMBR().getAreaEnlargement(record.getMBR());
        RTreeNode nextNode = node.getRecords().get(0).getChild();
        path.push(node);

        for (RTreeRecord treeRecord : node.getRecords()) {
            double areaEnlargement = treeRecord.getMBR().getAreaEnlargement(record.getMBR());
            if (areaEnlargement < minAreaEnlargement) {
                minAreaEnlargement = areaEnlargement;
                nextNode = treeRecord.getChild();
            }
        }

        if (nextNode.getHeight() > 0) {
            findInsertionPath(record, nextNode, path);
        }
        else {
            path.push(nextNode);
        }
        return path;
    }

    protected void performQuadraticSplitAndReadjustPath(RTreeNode node, ArrayDeque<RTreeNode> path) {
        ArrayList<RTreeNode> nodesAfterSplit = new ArrayList<>();
        quadraticSplit(node, nodesAfterSplit);

        boolean checkNodes = true;
        while (checkNodes) {
            checkNodes = false;
            int numberOfNodes = nodesAfterSplit.size();
            for(int i = 0; i < numberOfNodes; i++) {
                if (nodesAfterSplit.get(i).getRecordCount() > this.getM()) {
                    quadraticSplit(nodesAfterSplit.get(i), nodesAfterSplit);
                    nodesAfterSplit.remove(i);
                    checkNodes = true;
                }
            }
        }


        readjustPathAfterSplit(node, nodesAfterSplit, path);
    }

    protected void quadraticSplit(RTreeNode node, ArrayList<RTreeNode> nodesAfterSplit) {
        incrementSplitCount();

        RTreeNode node1 = createNodeWithoutRecords(node.getHeight());
        RTreeNode node2 = createNodeWithoutRecords(node.getHeight());

        findAndAssignFirstRecords(node, node1, node2);

        while(node.getRecordCount() > 0) {
            if (node1.getRecordCount() + node.getRecordCount() == this.getm()) {
                node1.addMultipleRecords(node.getRecords());
                node.clearRecords();
            }
            else if (node2.getRecordCount() + node.getRecordCount() == this.getm()) {
                node2.addMultipleRecords(node.getRecords());
                node.clearRecords();
            }

            else{
                findAndAssignNextRecord(node, node1, node2);
            }
        }

        node1.calculateAndUpdateMBR();
        node2.calculateAndUpdateMBR();

        if (this instanceof SFCRTreeStructure) {
            node1 = new SFCRTreeNode(node1.getId(), node1.getHeight(), node1.getRecords());
            node2 = new SFCRTreeNode(node2.getId(), node2.getHeight(), node2.getRecords());
        }

        nodesAfterSplit.add(node1);
        nodesAfterSplit.add(node2);
    }

    protected void findAndAssignFirstRecords(RTreeNode node, RTreeNode node1, RTreeNode node2) {
        double maxArea = 0;
        int initialSplitRecordIndex1 = 0;
        int initialSplitRecordIndex2 = 0;

        for (int i = 0; i < node.getRecordCount(); i++){
            for (int j = i + 1; j < node.getRecordCount(); j++){
                RTreeRecord record1 = node.getRecords().get(i);
                RTreeRecord record2 = node.getRecords().get(j);
                double area = getAreaOfTwoMBRs(record1, record2);
                if (maxArea < area){
                    maxArea = area;
                    initialSplitRecordIndex1 = i;
                    initialSplitRecordIndex2 = j;
                }
            }
        }

        node1.addRecordAndReadjustMBR(node.getRecords().get(initialSplitRecordIndex1));
        node2.addRecordAndReadjustMBR(node.getRecords().get(initialSplitRecordIndex2));

        if (initialSplitRecordIndex1 < initialSplitRecordIndex2) {
            node.removeRecord(initialSplitRecordIndex2);
            node.removeRecord(initialSplitRecordIndex1);
        }
        else {
            node.removeRecord(initialSplitRecordIndex1);
            node.removeRecord(initialSplitRecordIndex2);
        }
    }

    protected void findAndAssignNextRecord(RTreeNode node, RTreeNode node1, RTreeNode node2) {
        double maxDiffAreaEnlargement = -1;
        int indexNextRecord = 0;
        double areaEnlargementSelectedRecordNode1 = 0;
        double areaEnlargementSelectedRecordNode2 = 0;
        for (RTreeRecord record : node.getRecords()) {
            double areaEnlargementNode1 = node1.getMbr().getAreaEnlargement(record.getMBR());
            double areaEnlargementNode2 = node2.getMbr().getAreaEnlargement(record.getMBR());
            double diffAreaEnlargement = Math.abs(areaEnlargementNode1 - areaEnlargementNode2);
            if (diffAreaEnlargement > maxDiffAreaEnlargement) {
                maxDiffAreaEnlargement = diffAreaEnlargement;
                indexNextRecord = node.getRecordIndex(record);
                areaEnlargementSelectedRecordNode1 = areaEnlargementNode1;
                areaEnlargementSelectedRecordNode2 = areaEnlargementNode2;
            }
        }

        RTreeRecord recordToBeAdded = node.getRecords().get(indexNextRecord);
        node.removeRecord(indexNextRecord);

        if (areaEnlargementSelectedRecordNode1 <= areaEnlargementSelectedRecordNode2) {
            node1.addRecordAndReadjustMBR(recordToBeAdded);
        }
        else {
            node2.addRecordAndReadjustMBR(recordToBeAdded);
        }
    }

    protected abstract RTreeNode createNodeWithoutRecords(int height);

    private void readjustPathAfterSplit(RTreeNode nodeBeforeSplit, ArrayList<RTreeNode> nodesAfterSplit, ArrayDeque<RTreeNode> path){
        if (path.isEmpty()) {
            createNewRootNode(nodesAfterSplit);
        }
        else{
            RTreeNode parent = path.pop();

            int indexRecordToBeRemoved = -1;
            for (RTreeRecord record : parent.getRecords()) {
                if (record.getChild().getId() == nodeBeforeSplit.getId()) {
                    indexRecordToBeRemoved = parent.getRecordIndex(record);
                }
            }

            parent.removeRecord(indexRecordToBeRemoved);

            for (RTreeNode node : nodesAfterSplit) {
                parent.addRecord(createRecord(node));
            }

            RTreeNode parentBeforeAreaEnlargement = createNodeFromExistingNode(parent);
            parent.calculateAndUpdateMBR();

            if (this instanceof SFCRTreeStructure) {
                parent = new SFCRTreeNode(parent.getId(), parent.getHeight(), parent.getRecords());
            }

            if (parent.getRecordCount() <= getM()) {
                adjustMBRsOnPath(parent, parentBeforeAreaEnlargement, path);
            }
            else {
                performQuadraticSplitAndReadjustPath(parent, path);
            }
        }
    }

    protected abstract RTreeRecord createRecord(RTreeNode node);

    private double getAreaOfTwoMBRs(RTreeRecord record1, RTreeRecord record2) {
        float xLow = Float.min(record1.getMBR().getLow().getX(), record2.getMBR().getLow().getX());
        float yLow = Float.min(record1.getMBR().getLow().getY(), record2.getMBR().getLow().getY());
        float xHigh = Float.max(record1.getMBR().getHigh().getX(), record2.getMBR().getHigh().getX());
        float yHigh = Float.max(record1.getMBR().getHigh().getY(), record2.getMBR().getHigh().getY());

        float[] low = {xLow, yLow};
        float[] high = {xHigh, yHigh};

        MBR mbr = new MBR(low, high);

        return mbr.getArea();
    }

    protected void adjustMBRsOnPath(RTreeNode insertionNode, RTreeNode insertionNodeBefore, ArrayDeque<RTreeNode> path) {
        RTreeNode adjustedNode = createNodeFromExistingNode(insertionNode);
        boolean needsFurtherAdjusting = insertionNodeBefore.getMbr().getAreaEnlargement(adjustedNode.getMbr()) != 0;

        while(!path.isEmpty()) {
            RTreeNode parent = path.pop();
            int indexRecordToBeUpdated = -1;

            while(indexRecordToBeUpdated == -1) {
                for (RTreeRecord childRecord : parent.getRecords()) {
                    if (childRecord.getChild().getId() == adjustedNode.getId()) {
                        indexRecordToBeUpdated = parent.getRecordIndex(childRecord);
                        break;
                    }
                }
            }

            RTreeNode parentBeforeUpdate = createNodeFromExistingNode(parent);
            RTreeRecord updatedRecord;
            if (this instanceof GuttmannRTree) {
                GuttmannRTreeNode adjNode = (GuttmannRTreeNode) adjustedNode;
                updatedRecord = new GuttmannRTreeRecord(adjNode.getMbr(), adjNode);
            }
            else {
                SFCRTreeNode adjNode = (SFCRTreeNode) adjustedNode;
                updatedRecord = new SFCRTreeRecord(adjNode.getMbr(), adjNode, adjNode.getLowX());
            }

            parent.updateRecord(indexRecordToBeUpdated, updatedRecord);

            if (needsFurtherAdjusting) {
                parent.calculateAndUpdateMBR();

                if (this instanceof  SFCRTreeStructure) {
                    parent = new SFCRTreeNode(parent.getId(), parent.getHeight(), parent.getRecords());
                }

                needsFurtherAdjusting = parentBeforeUpdate.getMbr().getAreaEnlargement(parent.getMbr()) != 0;
            }

            adjustedNode = createNodeFromExistingNode(parent);
        }
        this.root = adjustedNode;
    }

    public abstract RTreeNode createNodeFromExistingNode(RTreeNode insertionNode);

    public void incrementSplitCount() {
        this.splitCount ++;
    }

    public int getSplitCount() {
        return this.splitCount;
    }
}

