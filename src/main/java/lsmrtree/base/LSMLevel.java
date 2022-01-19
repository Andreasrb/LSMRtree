package lsmrtree.base;

/**
 * Structure for each level in an LSM-tree (except lowest).
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public abstract class LSMLevel {
    private int threshold;
    private int size;
    private boolean isFull = false;

    public LSMLevel(int size, double thresholdPercent) {
        this.size = size;
        this.threshold = setThreshold(thresholdPercent);
    }

    private int setThreshold(double thresholdPercent) {
        return (int) Math.ceil(getSize()*thresholdPercent);
    }

    public int getThreshold() {
        return this.threshold;
    }

    public int getSize() {
        return this.size;
    }

    public boolean getIsFull() {
        return this.isFull;
    }

    public void setIsFull(boolean isFull) {
        this.isFull = isFull;
    }

}
