package fi.livi.rata.avoindata.updater.service.trainlocation;

import org.springframework.data.geo.Point;

import java.util.List;

public class TrainBoundary {
    List<Point> points;

    public TrainBoundary(final List<Point> boundingBox) {
        this.points = boundingBox;
    }

    public boolean contains(Point test) {
        int i;
        int j;
        boolean result = false;
        for (i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            final double iX = points.get(i).getX();
            final double iY = points.get(i).getY();
            final double jX = points.get(j).getX();
            final double jY = points.get(j).getY();

            final double testX = test.getX();
            final double testY = test.getY();

            if ((iY > testY) != (jY > testY) && (testX < (jX - iX) * (testY - iY) / (jY - iY) + iX)) {
                result = !result;
            }
        }
        return result;
    }
}
