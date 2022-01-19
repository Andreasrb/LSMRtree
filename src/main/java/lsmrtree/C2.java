package lsmrtree;

import rtree.seededClustering.LargeRTree;
import rtree.seededClustering.SFCRTreeRecord;
import rtree.seededClustering.SmallRTree;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Structure for level C2 in LSM-tree. Receives small R-trees and outliers to insert into largeTree in component.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class C2 {
    private LargeRTree largeTree;
    private Queue<SmallRTree> incomingSmallTrees;
    private Queue<SFCRTreeRecord> incomingOutliers;
    private boolean receivingRecords;

    // if want to start with empty component
    public C2(int dimensions, int m, int M) {
        this.largeTree = new LargeRTree(dimensions, m, M);
        this.incomingSmallTrees = new ConcurrentLinkedQueue<>();
        this.incomingOutliers = new ConcurrentLinkedQueue<>();
        this.receivingRecords = false;
    }

    // if want to start with existing data in component
    public C2(SmallRTree startTree) {
        this.largeTree = new LargeRTree(startTree);
        this.incomingSmallTrees = new ConcurrentLinkedQueue<>();
        this.incomingOutliers = new ConcurrentLinkedQueue<>();
        this.receivingRecords = false;
    }

    public void insertSmallTrees() {
        Queue<SmallRTree> smallTreesToInsert = new LinkedList<>(this.incomingSmallTrees);
        this.incomingSmallTrees.clear();
        this.largeTree.bulkInsert(smallTreesToInsert);
    }

    public void insertOutliers() {
        int numberOfOutliers = this.incomingOutliers.size();
        for (int i = 0; i < numberOfOutliers; i++) {
            this.largeTree.insert(this.incomingOutliers.remove());
            this.largeTree.incrementRecordCount();
        }
    }

    public LargeRTree getLargeTree() {
        return this.largeTree;
    }

    public void addIncomingSmallTrees(Queue<SmallRTree> incomingSmallTrees) {
        this.incomingSmallTrees.addAll(incomingSmallTrees);
    }

    public void addIncomingOutliers(Queue<SFCRTreeRecord> incomingOutliers) {
        this.incomingOutliers.addAll(incomingOutliers);
    }

    public void insertData() {
        if (!this.incomingSmallTrees.isEmpty()) {
            insertSmallTrees();
        }
        if (!this.incomingOutliers.isEmpty()) {
            insertOutliers();
        }
    }

    public boolean isIncomingRecordsHandled() {
        return this.incomingOutliers.isEmpty() && this.incomingSmallTrees.isEmpty() && !this.receivingRecords;
    }
}
