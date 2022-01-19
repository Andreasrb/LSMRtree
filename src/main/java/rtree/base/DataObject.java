package rtree.base;

/**
 * the data items coming in to the index structure
 *
 * @author Mari Sofie Lerfaldet <marisler@stud.ntnu.no>
 */
public class DataObject {
    private float lowX;
    private float lowY;
    private float highX;
    private float highY;

    public DataObject(float lowX, float lowY, float highX, float highY) {
        this.lowX = lowX;
        this.lowY = lowY;
        this.highX = highX;
        this.highY = highY;
    }

    public float getLowX() {
        return lowX;
    }

    public float getLowY() {
        return lowY;
    }

    public float getHighX() {
        return highX;
    }

    public float getHighY() {
        return highY;
    }
}
