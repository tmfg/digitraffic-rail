package fi.livi.rata.avoindata.updater.service.trainlocation;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

@Service
public class TrainLocationNearTrackFilterService {
    @Autowired
    private TrackBoundingBoxesService trackBoundingBoxesService;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public boolean isTrainLocationNearTrack(final TrainLocation trainLocation) {
            final RTree<TrainBoundary, Geometry> tree = trackBoundingBoxesService.getBoundingBoxes();

            if (isTrackLocationNearTrack(trainLocation.liikeLocation.getX(), trainLocation.liikeLocation.getY(), tree)) {
                return true;
            } else {
                log.info("Point {},{} ({}, {}) not in proximity of Tracks. TrainLocation: {}", trainLocation.location.getY(),
                        trainLocation.location.getX(), trainLocation.liikeLocation.getX(), trainLocation.liikeLocation.getY(),
                        trainLocation.trainLocationId);
                return false;
            }

    }

    private boolean isTrackLocationNearTrack(final double x, final double y, final RTree<TrainBoundary, Geometry> tree) {
        final Iterable<Entry<TrainBoundary, Geometry>> searchResult = tree.search(Geometries.point(x, y)).toBlocking().toIterable();

        for (final Entry<TrainBoundary, Geometry> trainBoundaryGeometryEntry : searchResult) {
            if (trainBoundaryGeometryEntry.value().contains(new Point(x, y))) {
                return true;
            }
        }

        return false;
    }
}
