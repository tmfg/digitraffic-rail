package fi.livi.rata.avoindata.updater.service.trainlocation;

import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class BoundingBoxService {

    // Source: https://math.stackexchange.com/questions/842773/bounding-box-of-a-thick-line-with-end-caps
    public List<Point> createBoundingBox(Point startingPoint, Point endingPoint, double distanceFromLine) {
        double x1 = startingPoint.getX();
        double y1 = startingPoint.getY();
        double x2 = endingPoint.getX();
        double y2 = endingPoint.getY();

        double w = distanceFromLine*2;

        if (startingPoint.equals(endingPoint)) {
            Point c1 = new Point(x1-w/2, y1-w/2);
            Point c2 = new Point(x1+w/2, y1-w/2);
            Point c3 = new Point(x1+w/2, y1+w/2);
            Point c4 = new Point(x1-w/2, y1+w/2);

            return Arrays.asList(c1, c2, c3, c4);
        }

        double d = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

        double a1 = (w * (x2 - x1)) / (2 * d);
        double a2 = (w * (y2 - y1)) / (2 * d);

        double b1 = -1 * a2;
        double b2 = a1;

        Point c1 = new Point(x1 - a1 + b1, y1 - a2 + b2);
        Point c2 = new Point(x1 - a1 - b1, y1 - a2 - b2);
        Point c3 = new Point(x2 + a1 - b1, y2 + a2 - b2);
        Point c4 = new Point(x2 + a1 + b1, y2 + a2 + b2);

        return Arrays.asList(c1, c2, c3, c4);
    }
}
