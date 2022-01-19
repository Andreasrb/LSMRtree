package rtree.seededClustering;

import rtree.base.RTreeNode;
import rtree.base.RTreeRecord;
import java.util.*;

/**
 * Seed tree, used to create clusters for small trees to be inserted into main R-tree in C2.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */

public class SeedTree extends SFCRTreeStructure{
    private int k; // level of seed tree from the large tree, NB! given as level from top height (root = 0) reverse from the tree height..
    private Queue<SFCRTreeRecord> outliers;
    private Hashtable<RTreeNode, ArrayList<SFCRTreeRecord>> clusters;
    private Queue<SFCRTreeRecord> incomingRecords;

    public SeedTree(int dimensions, int m, int M, int k, RTreeNode root, Queue<SFCRTreeRecord> incomingRecords) {
        super(dimensions, m, M, root);

        this.k = root.getHeight() - k; // need to calculate k from top to get correct level
        this.outliers = new LinkedList<>();
        this.clusters = new Hashtable<>();
        this.incomingRecords = new LinkedList<>(incomingRecords);

        constructClusters();
    }

    public void constructClusters() {
        findClusters();
        if (!this.clusters.isEmpty()) {
            ArrayList<RTreeNode> clustersToCheck = new ArrayList<>(this.clusters.keySet());
            checkClusterValidityAndFixTooSmallTrees(clustersToCheck);
        }
    }

    private void findClusters() {
        ArrayList<SFCRTreeRecord> clusteredRecords = new ArrayList<>();
        if (getRoot().getHeight() == this.k) {
            for (SFCRTreeRecord record : this.incomingRecords) {
                if (getRoot().getMbr().getAreaEnlargement(record.getMBR()) == 0) {
                    clusteredRecords.add(record);
                }
                else {
                    outliers.add(record);
                }
            }
            if (!clusteredRecords.isEmpty()) {
                this.clusters.put(getRoot(), clusteredRecords);
            }
        }
        else {
            findCorrectLevelAndSetClusterKeys(getRoot().getRecords());
            if (this.clusters.isEmpty()) {
                this.outliers.addAll(this.incomingRecords);
            }
            else {
                for (SFCRTreeRecord record : this.incomingRecords) {
                    boolean added = false;
                    for (RTreeNode node : this.clusters.keySet()) {
                        if (node.getMbr().getAreaEnlargement(record.getMBR()) == 0) {
                            if (!added) {
                                this.clusters.get(node).add(record);
                                added = true;
                                continue;
                            }
                        }
                    }
                    if (!added) {
                        this.outliers.add(record);
                    }
                }
            }
        }
    }

    private void findCorrectLevelAndSetClusterKeys(ArrayList<RTreeRecord> records) {
        for (RTreeRecord record : records) {
            if (record.getChild().getHeight() == this.k) {
                this.clusters.put(record.getChild(), new ArrayList<>());
            }
            else {
                findCorrectLevelAndSetClusterKeys(record.getChild().getRecords());
            }
        }
    }

    private void checkClusterValidityAndFixTooSmallTrees(ArrayList<RTreeNode> clustersToIterate) {
        for (RTreeNode node : clustersToIterate) {
            ArrayList<SFCRTreeRecord> records = this.clusters.get(node);
            if (records.isEmpty()) {
                this.clusters.remove(node);
                continue;
            }
            int heightOfConstructedSmallTree = checkHeightOfResultingTree(records);
            if(heightOfConstructedSmallTree < node.getHeight() - 1) {
                ArrayList<RTreeNode> subClusterKeys = findCorrectSubNodesForClustering(heightOfConstructedSmallTree, node);
                this.clusters.remove(node);

                for (SFCRTreeRecord record : records) {
                    boolean added = false;
                    for (RTreeNode subNode : subClusterKeys) {
                        if (subNode.getMbr().getAreaEnlargement(record.getMBR()) == 0) {
                            this.clusters.get(subNode).add(record);
                            added = true;
                            break;
                        }
                    }
                    if (!added) {
                        this.outliers.add(record);
                    }
                }
                checkClusterValidityAndFixTooSmallTrees(subClusterKeys);
            }
        }
    }

    private int checkHeightOfResultingTree(ArrayList<SFCRTreeRecord> records) {
        int resultingNodes = (int) Math.ceil((double)records.size()/getM());
        int height = 0;

        if (resultingNodes == 1) {
            return height;
        }

        while(resultingNodes != 1) {
            resultingNodes = (int) Math.ceil((double)resultingNodes/getM());
            height ++;

        }
        return height;
    }

    private ArrayList<RTreeNode> findCorrectSubNodesForClustering(int heightOfConstructedSmallTree, RTreeNode node) {
        ArrayList<RTreeNode> subClusterKeys = new ArrayList<>();
        for (RTreeRecord record : node.getRecords()) {
            if (record.getChild().getHeight() - 1 == heightOfConstructedSmallTree) {
                this.clusters.put(record.getChild(), new ArrayList<>());
                subClusterKeys.add(record.getChild());
            }
            else {
                findCorrectSubNodesForClustering(heightOfConstructedSmallTree, record.getChild());
            }
        }
        return subClusterKeys;
    }

    public Queue<SFCRTreeRecord> getIncomingRecords() {
        return incomingRecords;
    }

    public Queue<SFCRTreeRecord> getOutliers() {
        return outliers;
    }

    public Hashtable<RTreeNode, ArrayList<SFCRTreeRecord>> getClusters() {
        return this.clusters;
    }
}
