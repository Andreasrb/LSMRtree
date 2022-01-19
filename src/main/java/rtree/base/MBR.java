package rtree.base;

/**
 * Minimum Bounding Rectangle for a data item/data record.
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */

public class MBR {
    private Point low, high;

    public MBR(float[] low, float[] high) {
        this.low = new Point(low[0], low[1]);
        this.high = new Point(high[0], high[1]);
    }

    public MBR(MBR mbr) {
        this.low = new Point(mbr.getLow().getX(), mbr.getLow().getY());
        this.high = new Point(mbr.getHigh().getX(), mbr.getHigh().getY());
    }

    public double getAreaEnlargement(MBR newMBR){
        float xLow = Float.min(this.low.getX(), newMBR.getLow().getX());
        float yLow = Float.min(this.low.getY(), newMBR.getLow().getY());
        float xHigh = Float.max(this.high.getX(), newMBR.getHigh().getX());
        float yHigh = Float.max(this.high.getY(), newMBR.getHigh().getY());

        float[] low = {xLow, yLow};
        float[] high = {xHigh, yHigh};

        MBR mbr = new MBR(low, high);

        double newArea = mbr.getArea();

        return newArea - this.getArea();
    }

    public boolean isOverlapping(MBR newMBR) {
        if (this.high.getX() <= newMBR.low.getX() || newMBR.high.getX() <= this.low.getX()) {
            return false;
        }
        if (this.high.getY() <= newMBR.low.getY() || newMBR.high.getY() <= this.low.getY()) {
            return false;
        }
        else {
            return true;
        }
    }

    public double getArea(){
        double area = 1.0;
        area *= (this.high.getX() - this.low.getX());
        area *= (this.high.getY() - this.low.getY());

        return area;
    }

    public Point getLow() {
        return this.low;
    }

    public Point getHigh() {
        return this.high;
    }

    public int getLowX() {
        return (int)this.low.getX();
    }
}
