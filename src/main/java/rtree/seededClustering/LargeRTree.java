package rtree.seededClustering;

import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;
import java.util.*;

/**
 * RTree implemented with options to bulk-insert smaller trees into larger tree,
 * also inserts outliers OBO, as in seeded clustering method
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */

public class LargeRTree extends SFCRTreeStructure {
    private int repackCount;

    public LargeRTree(int dimensions, int m, int M) {
        super(dimensions, m, M, 0);
        this.repackCount = 0;

        RTreeNode root = new SFCRTreeNode(createNodeId(), 0, new ArrayList<>());
        setRoot(root);
    }

    public LargeRTree(SFCRTreeStructure tree) {
        super(tree.getDimensions(), tree.getm(), tree.getM(), tree.getRecordCount());
        setRoot(tree.getRoot());
    }

    /**
     * method for inserting input (small) trees into the main RTree
     * if main RTree is empty (just root) or only have few records, height = 0, will simply insert main tree into root
     *
     * NB! notice only take too high trees or trees that fit, too small trees are handled by adjusting the seeded tree before clustering is performed
     */
    public void bulkInsert(Queue<SmallRTree> inputTrees) {
        int treesToInsert = inputTrees.size();
        for (int i = 0; i < treesToInsert; i++) {
            SmallRTree tree = inputTrees.remove();
            RTreeNode insertionNode;
            ArrayDeque<RTreeNode> path = new ArrayDeque<>();

            if (this.getHeight() == 0) {
                insertionNode = this.getRoot();
            } else {
                path = findInsertionPathForSubtree(tree, this.getRoot(), path);
                insertionNode = path.pop();
            }

            RTreeNode insertionNodeBefore = this.createNodeFromExistingNode(insertionNode);

            if (tree.getHeight() == insertionNode.getHeight() - 1) {
                if (tree.getRoot().getRecordCount() < this.getm()) {
                    insertionNode = insertSubtreesWhenRootUnderflow(tree.getRoot(), insertionNode);
                }
                else {
                    insertionNode = insertSmallTreeAsWhole(tree.getRoot(), insertionNode);
                }
            }
            else if (tree.getHeight() >= insertionNode.getHeight()) {
                insertionNode = insertSubtreesOfSmallTree(tree, insertionNode);
            }

            if (insertionNode.getRecordCount() > this.getM()) {
                performQuadraticSplitAndReadjustPath(insertionNode, path);
            }
            else {
                adjustMBRsOnPath(insertionNode, insertionNodeBefore, path);
            }

            addToRecordCount(tree.getRecordCount());
        }
    }

    private ArrayDeque<RTreeNode> findInsertionPathForSubtree(SmallRTree tree, RTreeNode root, ArrayDeque<RTreeNode> path) {
        RTreeNode currentNode = root;

        if (currentNode.getId() == tree.getNodeIdOfSeed()) {
            path.add(currentNode);
        }
        else {
            checkSubPath(currentNode, tree, path);
        }
        return path;
    }

    private void checkSubPath(RTreeNode currentNode, SmallRTree tree, ArrayDeque<RTreeNode> path) {
        for (int i = 0; i < currentNode.getRecordCount(); i++) {
            RTreeNode child = currentNode.getRecords().get(i).getChild();
            if (child.getMbr().getAreaEnlargement(tree.getRoot().getMbr()) == 0) {
                if (child.getHeight() == tree.getHeightOfSeedNode()) {
                    if (child.getId() == tree.getNodeIdOfSeed()) {
                        path.add(child);
                    }
                }
                else if (path.isEmpty()) {
                    checkSubPath(child, tree, path);
                }
            }
        }
        if (!path.isEmpty()) {
            path.add(currentNode);
        }
    }

    private RTreeNode insertSmallTreeAsWhole(RTreeNode root, RTreeNode insertionNode) {
        SFCRTreeNode castedNode = (SFCRTreeNode) root;
        SFCRTreeRecord record = new SFCRTreeRecord(castedNode.getMbr(), castedNode, castedNode.getLowX());

        insertionNode = addSubtreeRepackEntriesIfNeeded(insertionNode, record);
        return insertionNode;
    }

    private RTreeNode insertSubtreesWhenRootUnderflow(RTreeNode root, RTreeNode insertionNode) {
        for (RTreeRecord record : root.getRecords()) {
            SFCRTreeNode selectedInsertionNode = (SFCRTreeNode) insertionNode.getRecords().get(0).getChild();
            double minAreaEnlargement = insertionNode.getRecords().get(0).getMBR().getAreaEnlargement(record.getMBR());
            for (RTreeRecord insertionRecord : insertionNode.getRecords()) {
                double areaEnlargement = insertionRecord.getMBR().getAreaEnlargement(record.getMBR());
                if (areaEnlargement < minAreaEnlargement) {
                    minAreaEnlargement = areaEnlargement;
                    selectedInsertionNode =  (SFCRTreeNode) insertionRecord.getChild();
                }
            }

            ArrayList<RTreeRecord> recordsToUpdate = new ArrayList<>();
            selectedInsertionNode.addRecordAndReadjustMBR(record);
            if (selectedInsertionNode.getRecordCount() > this.getM()) {
                ArrayList<SFCRTreeRecord> records = selectedInsertionNode.getRecordsAsSFC();
                Collections.sort(records);
                ArrayList<RTreeRecord> records1 = new ArrayList<>();
                ArrayList<RTreeRecord> records2 = new ArrayList<>();

                records1.addAll(records.subList(0, (int) Math.ceil((double)records.size()/2)));
                records2.addAll(records.subList((int) Math.ceil((double)records.size()/2), records.size()));

                RTreeNode node1 = new SFCRTreeNode(createNodeId(), selectedInsertionNode.getHeight(), records1);
                RTreeNode node2 = new SFCRTreeNode(createNodeId(), selectedInsertionNode.getHeight(), records2);

                RTreeRecord recordForNode1 = createRecord(node1);
                RTreeRecord recordForNode2 = createRecord(node2);
                recordsToUpdate.add(recordForNode1);
                recordsToUpdate.add(recordForNode2);
            }
            else {
                RTreeRecord updatedRecord = createRecord(selectedInsertionNode);
                recordsToUpdate.add(updatedRecord);
            }

            int indexOfRecordToBeUpdated = 0;
            for (RTreeRecord insertionRecord : insertionNode.getRecords()) {
                if (insertionRecord.getChild().getId() == selectedInsertionNode.getId()) {
                    indexOfRecordToBeUpdated = insertionNode.getRecordIndex(insertionRecord);
                }
            }

            insertionNode.removeRecord(indexOfRecordToBeUpdated);

            for (RTreeRecord updatedRecord : recordsToUpdate) {
                insertionNode.addRecordAndReadjustMBR(updatedRecord);
            }
        }
        return insertionNode;
    }

    private RTreeNode insertSubtreesOfSmallTree(SmallRTree tree, RTreeNode insertionNode) {
        ArrayList<RTreeRecord> records = tree.getRoot().getRecords();

        if (tree.getRoot().getHeight() != 0 && insertionNode.getHeight() != 0) {
            int currentHeightSmallSubTrees = tree.getRoot().getRecords().get(0).getChild().getHeight();
            while (currentHeightSmallSubTrees != insertionNode.getHeight() - 1) {
                ArrayList<RTreeRecord> nextRecords = new ArrayList<>();
                for (RTreeRecord record : records) {
                    nextRecords.addAll(record.getChild().getRecords());
                }
                records = nextRecords;
                currentHeightSmallSubTrees = records.get(0).getChild().getHeight();
            }
        }

        for (RTreeRecord record : records) {
            insertionNode = addSubtreeRepackEntriesIfNeeded(insertionNode, record);
        }

        return insertionNode;
    }

    /**
     * inserts subtree in insertionNode, repacks if necessary: repacking described below
     *  Check if root node of tree overlaps with any of the other mbrs in the insertionNode's children
     *  for the overlapping entries, repack the entries by bulk-loading (lowX-value etc)
     *  insert the resulting nodes back into the LargeTree
     *
     * @param insertionNode - node to insert the subtrees into
     * @param record - record of the subtree's root, which is to be inserted into insertionNode
     */
    private RTreeNode addSubtreeRepackEntriesIfNeeded(RTreeNode insertionNode, RTreeRecord record) {
        ArrayList<SFCRTreeRecord> noRepackRecords = new ArrayList<>();
        ArrayList<SFCRTreeRecord> newRecords = new ArrayList<>();
        newRecords.add((SFCRTreeRecord) record);

        for (RTreeRecord largeTreeRecord : insertionNode.getRecords()) {
            if (largeTreeRecord.getMBR().isOverlapping(record.getMBR())) {
                newRecords.add((SFCRTreeRecord) largeTreeRecord);
            }
            else {
                noRepackRecords.add((SFCRTreeRecord) largeTreeRecord);
            }
        }

        if (newRecords.size() == 1) {
            insertionNode.addRecordAndReadjustMBR(record);
            return new SFCRTreeNode((SFCRTreeNode) insertionNode);
        }

        ArrayList<SFCRTreeRecord> leafRecordsToRepack = new ArrayList<>();
        for (RTreeRecord newRecord : newRecords) {
            if (newRecord.isLeaf()) {
                leafRecordsToRepack.add((SFCRTreeRecord) newRecord);
            }
            else {
                findLeafRecords(newRecord, leafRecordsToRepack);
            }
        }

        int height = 0;
        ArrayList<SFCRTreeNode> nodes;
        Collections.sort(leafRecordsToRepack);
        while(true) {
            nodes = createNodesInBulk(leafRecordsToRepack, height);
            leafRecordsToRepack = createRecordsForNodes(nodes);
            Collections.sort(leafRecordsToRepack);

            if (height == insertionNode.getHeight() - 1) {
                break;
            }

            height ++;
        }

        if (leafRecordsToRepack.size() + noRepackRecords.size() < this.getm()) {
            leafRecordsToRepack = redistributeRecords(leafRecordsToRepack, this.getm() - noRepackRecords.size(), height);
        }

        ArrayList<SFCRTreeRecord> updatedRecords = new ArrayList<>();
        updatedRecords.addAll(leafRecordsToRepack);
        updatedRecords.addAll(noRepackRecords);

        Collections.sort(updatedRecords);

        insertionNode.clearRecords();
        insertionNode.addMultipleRecords(new ArrayList<>(updatedRecords));
        insertionNode.calculateAndUpdateMBR();

        this.repackCount ++;

        return insertionNode;
    }

    private ArrayList<SFCRTreeRecord> redistributeRecords(ArrayList<SFCRTreeRecord> records, int requiredNumberOfNodes, int height) {

        ArrayList<SFCRTreeRecord> recordsToPack = new ArrayList<>();
        for (SFCRTreeRecord record : records) {
            SFCRTreeNode child = (SFCRTreeNode) record.getChild();
            recordsToPack.addAll(child.getRecordsAsSFC());
        }

        Collections.sort(recordsToPack);

        int recordCountPerNode = (int) Math.floor((double) recordsToPack.size() / requiredNumberOfNodes);
        int recordsLeft = recordsToPack.size() - (recordCountPerNode * requiredNumberOfNodes);
        List<Integer> recordsPerNode = new ArrayList<>(Collections.nCopies(requiredNumberOfNodes, recordCountPerNode));

        for (int i = 0; i < recordsLeft; i++) {
            recordsPerNode.set(i, recordCountPerNode + 1);
        }

        int recordsAdded = 0;
        ArrayList<SFCRTreeNode> nodes = new ArrayList<>();
        for (int i = 0; i < requiredNumberOfNodes; i++) {
            ArrayList<RTreeRecord> childRecords = new ArrayList<>(recordsToPack.subList(recordsAdded, recordsAdded + recordsPerNode.get(i)));
            SFCRTreeNode node = new SFCRTreeNode(createNodeId(), height, childRecords);
            nodes.add(node);

            recordsAdded += recordsPerNode.get(i);
        }

        ArrayList<SFCRTreeRecord> repackedRecords = createRecordsForNodes(nodes);

        return repackedRecords;
    }

    private void findLeafRecords(RTreeRecord record, ArrayList<SFCRTreeRecord> leafRecordsToRepack) {
        for (RTreeRecord childRecord : record.getChild().getRecords()) {
            RTreeRecord currentRecord = childRecord;
            if (currentRecord.isLeaf()){
                leafRecordsToRepack.add((SFCRTreeRecord) currentRecord);
            }
            else {
                findLeafRecords(currentRecord, leafRecordsToRepack);
            }
        }
    }

    public int getRepackCount() {
        return this.repackCount;
    }
}
