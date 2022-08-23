package fi.livi.rata.avoindata.updater.service.trainlocation;

import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;

import java.util.List;


public class BoundingBoxServiceTest extends BaseTest {
    @Autowired
    private BoundingBoxService boundingBoxService;

    @Test
    public void horizontalBoundingBox() {
        final List<Point> boundingBox = boundingBoxService.createBoundingBox(new Point(1, 1), new Point(5, 1), 0.5);

        assertBoundingBox(boundingBox, new Point(0.5, 1.5), new Point(0.5, 0.5), new Point(5.5, 0.5), new Point(5.5, 1.5));
    }

    @Test
    public void verticalBoundingBox() {
        final List<Point> boundingBox = boundingBoxService.createBoundingBox(new Point(0, 0), new Point(0, 4), 0.5);

        assertBoundingBox(boundingBox, new Point(-0.5, -0.5), new Point(0.5, -0.5), new Point(0.5, 4.5), new Point(-0.5, 4.5));
    }

    @Test
    public void equalPoints() {
        final List<Point> boundingBox = boundingBoxService.createBoundingBox(new Point(0, 0), new Point(0, 0), 1);

        assertBoundingBox(boundingBox, new Point(-1, -1), new Point(1, -1), new Point(1, 1), new Point(-1, 1));
    }

    @Test
    public void inclinedBoundingBox() {
        final List<Point> boundingBox = boundingBoxService.createBoundingBox(new Point(1, 1), new Point(3, 3), Math.sqrt(2) / 2);

        assertBoundingBox(boundingBox, new Point(0, 1), new Point(1, 0), new Point(4, 3), new Point(3, 4));
    }

    private void assertBoundingBox(final List<Point> boundingBox, final Point point1, final Point point2, final Point point3,
            final Point point4) {
        Assertions.assertEquals(4, boundingBox.size());

        Assertions.assertEquals(boundingBox.get(0), point1);
        Assertions.assertEquals(boundingBox.get(1), point2);
        Assertions.assertEquals(boundingBox.get(2), point3);
        Assertions.assertEquals(boundingBox.get(3), point4);
    }
}