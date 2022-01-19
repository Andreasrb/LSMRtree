package lsmrtree;

import rtree.base.DataObject;
import rtree.seededClustering.SFCRTreeRecord;
import rtree.seededClustering.SmallRTree;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Structure for the whole LSM-tree. Consists of three levels (C0, C1 and C2).
 * C0 cannot receive objects if C1 or C2 is busy
 * C1 cannot receive objects if C2 is busy
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class LSMRTree{
    public C0 c0;
    public C1 c1;
    public C2 c2;

    private Queue<DataObject> incomingObjects;
    private int totalIncomingObjects;
    private boolean c0HandledLastObjects;
    private boolean c1HandledLastObjects;
    private boolean c2HandledLastObjects;

    public LSMRTree(int size, double thresholdPercent, int T, int dimensions, int m, int M) {
        this.c0 = new C0(size, thresholdPercent);
        this.c1 = new C1(size*T, thresholdPercent);
        this.c2 = new C2(dimensions, m, M);

        this.c0HandledLastObjects = false;
        this.c1HandledLastObjects = false;
        this.c2HandledLastObjects = false;
    }

    public LSMRTree(int size, double thresholdPercent, int T, SmallRTree startTree) {
        this.c0 = new C0(size, thresholdPercent);
        this.c1 = new C1(size*T, thresholdPercent);
        this.c2 = new C2(startTree);

        this.c0HandledLastObjects = false;
        this.c1HandledLastObjects = false;
        this.c2HandledLastObjects = false;
    }

    public void handleTransitionFromC0ToC1() {
        this.c0.flushRecords();
        Queue<SFCRTreeRecord> flushedRecords = this.c0.getFlushedRecords();

        this.c0.setIsFull(false);

        if (this.c1.getIsFull()) {
            handleTransitionFromC1ToC2();
        }

        this.c1.addIncomingRecords(flushedRecords);

        if (this.c0HandledLastObjects) {
            this.c1HandledLastObjects = true;
            this.c1.setLastRecordsRetrieved(true);
        }
    }

    public void handleTransitionFromC1ToC2() {
        this.c1.setLargeTree(this.c2.getLargeTree());

        this.c1.handleIncomingRecords();
        this.c1.setIsFull(false);

        this.c1.flushTreesAndOutliers();
        Queue<SmallRTree> flushedTrees = this.c1.getFlushedTrees();
        Queue<SFCRTreeRecord> flushedOutliers = this.c1.getFlushedOutliers();

        this.c2.addIncomingSmallTrees(flushedTrees);
        this.c2.addIncomingOutliers(flushedOutliers);

        this.c2.insertData();

        if (this.c1HandledLastObjects) {
            this.c2HandledLastObjects = true;
        }
    }

    /**
     * checks if last records to be inserted have been inserted, and C0 or C1 needs to be flushed without being full.
     * later this could be initiated by a time limit, but doing it this way now in order to finish
     */
    public void checkIfLastRecordsInserted() {
        if ((this.c0.getTotalRecordsHandled() == this.totalIncomingObjects) && !this.c0.levelIsEmpty()) {
            this.c0.setIsFull(true);
            this.c0HandledLastObjects = true;
        }

        if ((this.c1.getTotalRecordsHandled() == this.totalIncomingObjects || this.c1HandledLastObjects) && !this.c1.isComponentEmpty()) {
            this.c1.setIsFull(true);
        }
    }

    public void run(Queue<DataObject> incomingObjects) {
        this.incomingObjects = new LinkedList<>(incomingObjects);
        this.totalIncomingObjects = incomingObjects.size();

        int resultingLargeTreeSize = this.c2.getLargeTree().getRecordCount() + this.incomingObjects.size();

        while (this.c2.getLargeTree().getRecordCount() < resultingLargeTreeSize) {
            if (!this.incomingObjects.isEmpty() && !this.c0.getIsFull()) {
                this.c0.addRecord(this.incomingObjects.remove());
            }

            checkIfLastRecordsInserted();

            if (this.c0.getIsFull()) {
                handleTransitionFromC0ToC1();
            }

            if (this.c1.getIsFull()) {
                handleTransitionFromC1ToC2();
            }
        }
    }

    public boolean isC0HandledLastObjects() {
        return c0HandledLastObjects;
    }

    public boolean isC1HandledLastObjects() {
        return c1HandledLastObjects;
    }

    public boolean isC2HandledLastObjects() {
        return c2HandledLastObjects;
    }
}
