package fi.livi.rata.avoindata.updater.service.gtfs;

import static org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.osgeo.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSShapeService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrakediaRouteService trakediaRouteService;

    @Autowired
    private TrakediaLiikennepaikkaService liikennepaikkaService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    public List<Shape> createShapesFromTrips(List<Trip> trips, Map<String, Stop> stopMap) {
        ZonedDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC);
        Map<String, JsonNode> trakediaNodes = liikennepaikkaService.getTrakediaLiikennepaikkaNodes(startOfDay);

        Map<String, List<Shape>> shapeCache = new HashMap<>();
        for (Trip trip : trips) {
            log.info("Creating shape for trip {}", trip.tripId);

            String stops = trip.stopTimes.stream().map(s -> s.stopId).collect(Collectors.joining(">"));

            List<Shape> shapes = shapeCache.get(stops);
            if (shapes == null) {
                shapes = createShapes(stopMap, trakediaNodes, trip, stops);
                shapeCache.put(stops, shapes);
            }

            trip.shapeId = stops;
        }
        return shapeCache.values().stream().flatMap(s -> s.stream()).collect(Collectors.toList());
    }

    private List<Shape> createShapes(Map<String, Stop> stopMap, Map<String, JsonNode> trakediaNodes, Trip trip, String stops) {
        GeometryFactory geometryFactory = new GeometryFactory();

        List<double[]> tripPoints = new ArrayList<>();
        for (int i = 0; i < trip.stopTimes.size() - 1; i++) {
            StopTime startStopTime = trip.stopTimes.get(i);
            StopTime endStopTime = trip.stopTimes.get(i + 1);

            Stop startStop = stopMap.get(startStopTime.stopId);
            Stop endStop = stopMap.get(endStopTime.stopId);

            try {
                JsonNode startTrakediaNode = trakediaNodes.get(startStop.stopId);
                JsonNode endTrakediaNode = trakediaNodes.get(endStop.stopId);

                String startTunniste = startTrakediaNode.get(0).get("tunniste").textValue();
                String endTunniste = endTrakediaNode.get(0).get("tunniste").textValue();

//                log.info("Creating shape for {} -> {}", startStop.stopCode, endStop.stopCode);
                tripPoints.addAll(this.trakediaRouteService.createRoute(startStop, endStop, startTunniste, endTunniste));
            } catch (Exception e) {
                log.warn("Creating route failed for {} -> {}", startStop.stopCode, endStop.stopCode, e);
                ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
                ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
                tripPoints.addAll(List.of(new double[]{start.x, start.y}, new double[]{end.x, end.y}));
            }
        }

        List<Coordinate> coordinates = tripPoints.stream().map(s -> new Coordinate(s[0], s[1])).collect(Collectors.toList());

        CoordinateSequence points = new CoordinateArraySequence(coordinates.toArray(new Coordinate[0]));
        LineString simplifiedGeomerty = (LineString) simplify(new LineString(points, geometryFactory), 2.0);
        Coordinate[] simplifiedCoordinates = simplifiedGeomerty.getCoordinates();

        List<Shape> tripsShapes = new ArrayList<>();
        for (int i1 = 0; i1 < simplifiedCoordinates.length; i1++) {
            Coordinate point = simplifiedCoordinates[i1];

            ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(point.x, point.y);

            Shape shape = new Shape();
            shape.shapeId = stops;
            shape.longitude = projCoordinate.x;
            shape.latitude = projCoordinate.y;
            shape.sequence = i1;


            tripsShapes.add(shape);
        }
        return tripsShapes;
    }


}
