package lsmrtree;

import lsmrtree.base.LSMLevel;
import rtree.base.RTreeNode;
import rtree.seededClustering.LargeRTree;
import rtree.seededClustering.SFCRTreeRecord;
import rtree.seededClustering.SeedTree;
import rtree.seededClustering.SmallRTree;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Structure for level C1 in LSM-tree. Takes incoming objects, when threshold reached -> gets seed tree from lower level, clusters objects, builds small trees.
 * flushes small trees (fully packed by bulk insertion) and outliers
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class C1 extends LSMLevel {
    private Queue<SmallRTree> smallTrees;
    private Queue<SFCRTreeRecord> outliers;
    private Queue<SFCRTreeRecord> incomingRecords;
    private LargeRTree largeTree;
    private Queue<SmallRTree> flushedTrees;
    private Queue<SFCRTreeRecord> flushedOutliers;
    private boolean lastRecordsRetrieved;
    private int totalRecordsHandled;
    private int k;

    public C1(int size, double thresholdPercent) {
        super(size, thresholdPercent);

        this.smallTrees = new ConcurrentLinkedQueue<>();
        this.outliers = new ConcurrentLinkedQueue<>();
        this.incomingRecords = new ConcurrentLinkedQueue<>();
        this.flushedTrees = new ConcurrentLinkedQueue<>();
        this.flushedOutliers = new ConcurrentLinkedQueue<>();
        this.totalRecordsHandled = 0;
        this.lastRecordsRetrieved = false;
    }

    public void handleIncomingRecords() {
        if (this.lastRecordsRetrieved && this.largeTree != null && (!this.incomingRecords.isEmpty())) {
            constructSmallTreesAndOutliers();
        }

        if (getIsFull() && this.largeTree != null) {
            constructSmallTreesAndOutliers();
        }
    }

    private void constructSmallTreesAndOutliers() {
        this.k = this.largeTree.getHeight()/2;

        Queue<SFCRTreeRecord> currentRecords = new LinkedList<>(this.incomingRecords);
        this.incomingRecords.clear();
        setIsFull(false);

        this.totalRecordsHandled += currentRecords.size();

        SeedTree seedTree = new SeedTree(this.largeTree.getDimensions(), this.largeTree.getm(), this.largeTree.getM(), this.k, this.largeTree.createNodeFromExistingNode(this.largeTree.getRoot()), currentRecords);

        for (RTreeNode clusterKey : seedTree.getClusters().keySet()) {
            ArrayList<SFCRTreeRecord> clusterRecords = seedTree.getClusters().get(clusterKey);
            if (clusterRecords.size() != 0) {
                SmallRTree smallTree = new SmallRTree(this.largeTree.getDimensions(), this.largeTree.getm(), this.largeTree.getM(), clusterRecords, clusterKey.getId(), clusterKey.getHeight());

                this.smallTrees.add(smallTree);
            }
        }

        this.outliers.addAll(seedTree.getOutliers());
        this.largeTree = null;
    }

    public void flushTreesAndOutliers() {
        this.flushedTrees.addAll(this.smallTrees);
        this.smallTrees.clear();

        this.flushedOutliers.addAll(this.outliers);
        this.outliers.clear();
    }

    public Queue<SmallRTree> getFlushedTrees() {
        Queue<SmallRTree> flushedTrees = new ConcurrentLinkedQueue<>(this.flushedTrees);
        this.flushedTrees.clear();

        return flushedTrees;
    }

    public Queue<SFCRTreeRecord> getFlushedOutliers() {
        Queue<SFCRTreeRecord> flushedOutliers = new ConcurrentLinkedQueue<>(this.flushedOutliers);
        this.flushedOutliers.clear();

        return flushedOutliers;
    }

    public synchronized void addIncomingRecords(Queue<SFCRTreeRecord> incomingRecords) {
        this.incomingRecords.addAll(incomingRecords);
        if (this.incomingRecords.size() >= getThreshold()) {
            setIsFull(true);
        }
    }

    public void setLargeTree(LargeRTree currentLargeTree) {
        this.largeTree = currentLargeTree;
    }

    public int getTotalRecordsHandled() {
        return this.totalRecordsHandled;
    }

    public boolean isComponentEmpty() {
        return this.smallTrees.isEmpty() && this.outliers.isEmpty() && this.incomingRecords.isEmpty();
    }

    public void setLastRecordsRetrieved(boolean lastRecordsRetrieved) {
        this.lastRecordsRetrieved = lastRecordsRetrieved;
    }

    public boolean incomingRecordsEmpty() {
        return this.incomingRecords.isEmpty();
    }
}
