package rtree.base;

import java.util.ArrayList;

/**
 * Node in RTree, contains records. If leaf node, need to check if height equal to total tree height in RTree.java
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public abstract class RTreeNode {
    private int id;
    private int height;
    private MBR mbr;
    private ArrayList<RTreeRecord> records;

    public RTreeNode(int id, int height, ArrayList<RTreeRecord> records) {
        this.id = id;
        this.height = height;
        this.records = new ArrayList<>(records);

        if (!records.isEmpty()) {
            calculateAndUpdateMBR();
        }
        else {
            float[] low = {-1,-1};
            float[] high = {-1,-1};
            this.mbr = new MBR(low, high);
        }
    }

    public RTreeNode(RTreeNode node){
        this.id = node.getId();
        this.height = node.getHeight();
        this.mbr = new MBR(node.getMbr());
        this.records = new ArrayList<>(node.getRecords());
    }

    public void addRecordAndReadjustMBR(RTreeRecord record) {
        this.records.add(record);
        calculateAndUpdateMBR();
    }

    public void addRecord(RTreeRecord record) {
        this.records.add(record);
    }

    public void addMultipleRecords(ArrayList<RTreeRecord> records) {
        this.records.addAll(records);
    }

    public void updateRecord(int indexOfRecord, RTreeRecord updatedRecord) {
        this.records.set(indexOfRecord, updatedRecord);
    }

    public void removeRecord(int indexOfRecord) {
        this.records.remove(indexOfRecord);
    }

    public void clearRecords() {
        this.records.clear();
    }

    public int getRecordCount() {
        return this.records.size();
    }

    public int getRecordIndex(RTreeRecord record) {
        return this.records.indexOf(record);
    }

    public ArrayList<RTreeRecord> getRecords() {
        return records;
    }

    public void calculateAndUpdateMBR() {
        float xLow = this.records.get(0).getMBR().getLow().getX();
        float yLow = this.records.get(0).getMBR().getLow().getY();
        float xHigh = this.records.get(0).getMBR().getHigh().getX();
        float yHigh = this.records.get(0).getMBR().getHigh().getY();

        for (RTreeRecord record : this.records) {
            if (record.getMBR().getLow().getX() < xLow) {
                xLow = record.getMBR().getLow().getX();
            }
            if (record.getMBR().getLow().getY() < yLow) {
                yLow = record.getMBR().getLow().getY();
            }
            if (record.getMBR().getHigh().getX() > xHigh) {
                xHigh = record.getMBR().getHigh().getX();
            }
            if (record.getMBR().getHigh().getY() > yHigh) {
                yHigh = record.getMBR().getHigh().getY();
            }
        }

        float[] low = {xLow, yLow};
        float[] high = {xHigh, yHigh};

        this.mbr = new MBR(low, high);
    }

    public MBR getMbr() {
        return mbr;
    }

    public int getId() {
        return this.id;
    }

    public int getHeight() {
        return this.height;
    }
}
