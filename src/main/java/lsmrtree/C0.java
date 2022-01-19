package lsmrtree;

import lsmrtree.base.LSMLevel;
import rtree.base.DataObject;
import rtree.base.MBR;
import rtree.seededClustering.SFCRTreeRecord;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Structure for the traditional in-memory component in the LSM-tree.
 * In this implementation whole tree in-memory, acts as receiver of incoming objects, wraps them in records,
 * Sends records to next level when threshold reached.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class C0 extends LSMLevel {
    private ArrayList<SFCRTreeRecord> records;
    private Queue<SFCRTreeRecord> flushedRecords;
    private int totalRecordsHandled;

    public C0(int size, double thresholdPercent) {
        super(size, thresholdPercent);

        this.records = new ArrayList<>();
        this.flushedRecords = new ConcurrentLinkedQueue<>();
        this.totalRecordsHandled = 0;
    }

    public void addRecord(DataObject dataObject) {
        float[] low = {dataObject.getLowX(), dataObject.getLowY()};
        float[] high = {dataObject.getHighX(), dataObject.getHighY()};
        MBR mbr = new MBR(low, high);

        SFCRTreeRecord record = new SFCRTreeRecord(mbr, dataObject);

        this.records.add(record);

        if (this.records.size() == getThreshold()) {
            setIsFull(true);
        }

        this.totalRecordsHandled ++;
    }

    public void flushRecords() {
        this.flushedRecords = new ConcurrentLinkedQueue<>(this.records);
        this.records.clear();
    }

    public Queue<SFCRTreeRecord> getFlushedRecords() {
        Queue<SFCRTreeRecord> flushedRecords = new ConcurrentLinkedQueue<>(this.flushedRecords);
        this.flushedRecords.clear();

        return flushedRecords;
    }

    public boolean levelIsEmpty() {
        return this.records.isEmpty();
    }

    public int getTotalRecordsHandled() {
        return this.totalRecordsHandled;
    }
}
