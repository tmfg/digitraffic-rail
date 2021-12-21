package fi.livi.rata.avoindata.updater.service.gtfs.djikstra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;

@Service
public class NearestPointsService {
    public List<Coordinate> kClosest(List<Coordinate> points, Coordinate startPoint, int k) {

        List<DistanceResult> results = new ArrayList<>();
        for (Coordinate point : points) {
            results.add(new DistanceResult(point, this.distanceBetweenTwoPoints(startPoint, point)));
        }

        List<Coordinate> sorted = results.stream().sorted(Comparator.comparing(o -> o.distance)).map(s -> s.point).collect(Collectors.toList());

        return sorted.subList(0, Math.min(k, sorted.size()));
    }

    private double distanceBetweenTwoPoints(Coordinate pointA, Coordinate pointB) {
        return Math.sqrt((pointB.x - pointA.x) * (pointB.x - pointA.x) + (pointB.y - pointA.y) * (pointB.y - pointA.y));
    }

    class DistanceResult {
        public final Coordinate point;
        public final Double distance;

        public DistanceResult(Coordinate point, double distance) {
            this.point = point;
            this.distance = distance;
        }
    }
}

