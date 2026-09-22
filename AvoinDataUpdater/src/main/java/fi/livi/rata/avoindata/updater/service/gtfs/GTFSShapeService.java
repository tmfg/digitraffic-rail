package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.observability.LogFields;
import fi.livi.rata.avoindata.updater.service.gtfs.observability.GtfsRunScope;
import fi.livi.rata.avoindata.updater.service.gtfs.observability.ShapeMetricsSink;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSShapeService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrakediaRouteService trakediaRouteService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private StoptimesSplitterService stoptimesSplitterService;

    public List<Shape> createShapesFromTrips(final List<Trip> trips, final Map<String, Stop> stopMap,
                                             final Map<String, JsonNode> trakediaNodes, final LocalDate routeDate,
                                             final FailedSegments failedSegments) {
        final ShapeMetricsSink metrics = GtfsRunScope.shapeMetrics();
        final Map<Integer, List<Shape>> shapeCache = new HashMap<>();
        final int distinctShapes = (int) trips.stream()
                .map(GTFSShapeService::shapeKey)
                .distinct()
                .count();
        int processedShapes = 0;
        int realShapes = 0;
        for (final Trip trip : trips) {
            final Integer stops = shapeKey(trip);

            if (!shapeCache.containsKey(stops)) {
                final ShapeGroup group = createShapes(stopMap, trakediaNodes, trip, stops, routeDate, failedSegments);
                shapeCache.put(stops, group.shapes());
                if (group.complete()) {
                    realShapes += group.shapes().size();
                }
                metrics.recordShapeProcessed(++processedShapes, distinctShapes)
                        .ifPresent(heartbeat -> log.info("{}", LogFields.of(heartbeat)));
            }

            trip.shapeId = stops;
        }
        final List<Shape> shapes = shapeCache.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        metrics.recordShapes(shapes.size(), realShapes);
        if (realShapes < shapes.size()) {
            metrics.recordFeedDegraded();
        }
        return shapes;
    }

    private static Integer shapeKey(final Trip trip) {
        return trip.stopTimes.stream().map(s -> s.stopId).collect(Collectors.joining(">")).hashCode();
    }

    /** Shapes for one stop sequence; incomplete when any segment fell back to straight-line geometry. */
    private record ShapeGroup(List<Shape> shapes, boolean complete) {
    }

    private ShapeGroup createShapes(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes,
                                    final Trip trip, final int stops, final LocalDate routeDate,
                                    final FailedSegments failedSegments) {
        final List<StopTime> actualStops = this.stoptimesSplitterService.splitStoptimes(trip);

        final TripGeometry geometry = buildTripGeometry(stopMap, trakediaNodes, actualStops, trip, routeDate, failedSegments);

        final List<Shape> tripsShapes = new ArrayList<>();
        for (int i1 = 0; i1 < geometry.coordinates().size(); i1++) {
            final Coordinate point = geometry.coordinates().get(i1);

            final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(point.x, point.y);

            final Shape shape = new Shape();
            shape.shapeId = stops;
            shape.longitude = projCoordinate.x;
            shape.latitude = projCoordinate.y;
            shape.sequence = i1;

            tripsShapes.add(shape);
        }
        return new ShapeGroup(tripsShapes, geometry.complete());
    }


    /** Geometry for one trip; incomplete when any segment fell back to straight-line geometry. */
    private record TripGeometry(List<Coordinate> coordinates, boolean complete) {
    }

    private TripGeometry buildTripGeometry(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes,
                                           final List<StopTime> stopTimes, final Trip trip, final LocalDate routeDate,
                                           final FailedSegments failedSegments) {
        final List<Coordinate> tripPoints = new ArrayList<>();
        boolean complete = true;
        for (int i = 0; i < stopTimes.size() - 1; i++) {
            final Stop startStop = stopMap.get(stopTimes.get(i).stopId);
            final Stop endStop = stopMap.get(stopTimes.get(i + 1).stopId);

            final SegmentGeometry segment =
                    fetchSegmentGeometry(trakediaNodes, startStop, endStop, trip, routeDate, failedSegments);

            tripPoints.addAll(segment.coordinates());
            complete &= !segment.fallback();
        }

        return new TripGeometry(tripPoints, complete);
    }

    /** Always usable; {@code fallback} marks straight-line geometry. */
    private record SegmentGeometry(List<Coordinate> coordinates, boolean fallback) {
    }

    private SegmentGeometry fetchSegmentGeometry(final Map<String, JsonNode> trakediaNodes, final Stop startStop,
                                                 final Stop endStop, final Trip trip, final LocalDate routeDate,
                                                 final FailedSegments failedSegments) {
        final String segment = FailedSegments.key(startStop.stopId, endStop.stopId);
        try {
            final JsonNode startTrakediaNode = trakediaNodes.get(startStop.stopId);
            final JsonNode endTrakediaNode = trakediaNodes.get(endStop.stopId);

            if (startTrakediaNode == null || startTrakediaNode.size() == 0) {
                return fallback(startStop, endStop, NoGeometryReason.NO_START_NODE);
            }
            if (endTrakediaNode == null || endTrakediaNode.size() == 0) {
                return fallback(startStop, endStop, NoGeometryReason.NO_END_NODE);
            }
            if (failedSegments.contains(segment)) {
                return fallback(startStop, endStop, NoGeometryReason.PREVIOUSLY_FAILED);
            }

            final String startTunniste = startTrakediaNode.get(0).get("tunniste").textValue();
            final String endTunniste = endTrakediaNode.get(0).get("tunniste").textValue();

            // Counted before the call so a throwing cache miss stays in the hit/miss denominator.
            GtfsRunScope.shapeMetrics().recordRouteLookup();
            final Optional<List<Coordinate>> route = this.trakediaRouteService.createRoute(startStop, endStop,
                    startTunniste, endTunniste, routeDate);

            if (route.isEmpty()) {
                // Empty geometry and no-path are failures too; without this they are re-requested
                // by every later feed.
                failedSegments.record(segment);
                return fallbackWithReasonAlreadyRecorded(startStop, endStop);
            }
            GtfsRunScope.shapeMetrics().recordRealSegment();
            return new SegmentGeometry(route.get(), false);
        } catch (final WebClientResponseException e) {
            failedSegments.record(segment);
            // Path only: the provider splits values on '=', so a query string would truncate it.
            GtfsRunScope.shapeMetrics().recordRouteFailure(segment,
                            e.getRequest() == null ? "" : e.getRequest().getURI().getPath(),
                            e.getStatusCode().value(), e.getClass().getSimpleName())
                    .ifPresent(sample -> log.warn("{}", LogFields.of(sample)));
            return fallback(startStop, endStop, NoGeometryReason.ROUTE_HTTP_ERROR);
        } catch (final Exception e) {
            failedSegments.record(segment);
            log.error("method=fetchSegmentGeometry tripId={} startStop={} endStop={} error.type={}", trip.tripId,
                    startStop.stopCode, endStop.stopCode, e.getClass().getSimpleName(), e);
            return fallback(startStop, endStop, NoGeometryReason.ROUTE_PROCESSING_ERROR);
        }
    }

    private SegmentGeometry fallback(final Stop startStop, final Stop endStop, final NoGeometryReason reason) {
        GtfsRunScope.shapeMetrics().recordNoGeometryReason(reason);
        return fallbackWithReasonAlreadyRecorded(startStop, endStop);
    }

    /** TrakediaRouteService records the reasons it owns, so only the segment totals are added here. */
    private SegmentGeometry fallbackWithReasonAlreadyRecorded(final Stop startStop, final Stop endStop) {
        GtfsRunScope.shapeMetrics().recordDummySegment(startStop.stopCode);
        return new SegmentGeometry(createDummyRoute(startStop, endStop), true);
    }

    private List<Coordinate> createDummyRoute(final Stop startStop, final Stop endStop) {
        final ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        final ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
        final List<Coordinate> dummyRoute = List.of(new Coordinate(start.x, start.y), new Coordinate(end.x, end.y));
        return dummyRoute;
    }


}
