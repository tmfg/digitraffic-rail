package fi.livi.rata.avoindata.updater.service.gtfs;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.locationtech.proj4j.ProjCoordinate;

import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GTFSShapeServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Duration FEED_BUDGET = Duration.ofMinutes(10);

    private GTFSShapeService service;
    private TrakediaRouteService routeService;
    private StoptimesSplitterService splitterService;
    private GtfsRunContext context;
    private GtfsRunMetrics metrics;

    @BeforeEach
    void setUp() throws Exception {
        service = new GTFSShapeService();
        routeService = mock(TrakediaRouteService.class);
        splitterService = mock(StoptimesSplitterService.class);
        context = new GtfsRunContext(FEED_BUDGET, Clock.systemUTC());
        metrics = context.metrics();
        final Wgs84ConversionService conversionService = mock(Wgs84ConversionService.class);
        when(conversionService.wgs84Tolivi(org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.anyDouble())).thenReturn(new ProjCoordinate(1, 1));
        when(conversionService.liviToWgs84(org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.anyDouble())).thenReturn(new ProjCoordinate(1, 1));
        setField("trakediaRouteService", routeService);
        setField("wgs84ConversionService", conversionService);
        setField("stoptimesSplitterService", splitterService);
    }

    @Test
    void givenWebClientResponseFailureWhenShapesAreCreatedThenFailureIsStructuredWithoutTripDetails() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node"))
                .thenThrow(WebClientResponseException.create(503, "unavailable", null, null, null));

        // When
        createShapes(Map.of("AAA", start, "BBB", end),
                Map.of("AAA", node("start-node"), "BBB", node("end-node")), trip);

        // Then
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.route_http_error", 1);
        final Map<String, Object> sample = metrics.routeFailureSamples().getFirst();
        assertThat(sample.get("http.response.status_code")).isEqualTo(503);
        assertThat(sample.get("error.type")).isEqualTo("ServiceUnavailable");
        assertThat(sample.values())
                .noneMatch(value -> String.valueOf(value).contains("scheduleRows"));
    }

    @Test
    void givenMissingNodesWhenShapesAreCreatedThenACounterReplacesThePerSegmentWarning() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");

        // When
        createShapes(Map.of("AAA", start, "BBB", end), Map.of(), trip);

        // Then
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.no_start_node", 1);
    }

    @Test
    void givenResolvedGeometryWhenShapesAreCreatedThenTheSegmentAndShapeCountAsReal() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node"))
                .thenReturn(TrakediaRouteService.RouteResult.of(List.of(new Coordinate(1, 1), new Coordinate(2, 2))));

        // When
        final List<Shape> shapes = createShapes(Map.of("AAA", start, "BBB", end),
                Map.of("AAA", node("start-node"), "BBB", node("end-node")), trip);

        // Then
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.real", 1)
                .containsEntry("rail.gtfs.segments.dummy", 0)
                .containsEntry("rail.gtfs.shapes.total", shapes.size())
                .containsEntry("rail.gtfs.shapes.real", shapes.size());
        assertThat(metrics.outcome()).isEqualTo(GtfsOutcome.SUCCESS);
    }

    @Test
    void givenEmptyGeometryWhenShapesAreCreatedThenItIsNotCountedAsTransportFailure() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node"))
                .thenReturn(TrakediaRouteService.RouteResult.fallback(DummyReason.ROUTE_EMPTY_GEOMETRY));

        // When
        createShapes(Map.of("AAA", start, "BBB", end),
                Map.of("AAA", node("start-node"), "BBB", node("end-node")), trip);

        // Then an HTTP 200 with no geometry is a data problem, not a transport one
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.route_empty_geometry", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.route_http_error", 0);
        assertThat(metrics.routeFailureSamples()).isEmpty();
    }

    @Test
    void givenFallbackGeometryWhenShapesAreCreatedThenTheFeedIsMarkedDegraded() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        context.startFeed("gtfs-all.zip");

        // When
        createShapes(Map.of("AAA", start, "BBB", end), Map.of(), trip);

        // Then
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.shapes.real", 0)
                .containsEntry("rail.gtfs.feeds.degraded", "gtfs-all.zip");
        assertThat(metrics.outcome()).isEqualTo(GtfsOutcome.DEGRADED);
    }

    @Test
    void givenRepeatedFailingSegmentWhenASecondFeedRunsThenTheRouteIsNotRequestedAgain() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node"))
                .thenThrow(WebClientResponseException.create(503, "unavailable", null, null, null));
        final Map<String, Stop> stops = Map.of("AAA", start, "BBB", end);
        final Map<String, JsonNode> nodes = Map.of("AAA", node("start-node"), "BBB", node("end-node"));
        createShapes(stops, nodes, trip("trip-1", "AAA", "BBB"));

        // When the same pair appears again in a later feed
        createShapes(stops, nodes, trip("trip-2", "AAA", "BBB"));

        // Then only the first occurrence issued a request
        org.mockito.Mockito.verify(routeService, org.mockito.Mockito.times(1))
                .createRoute(start, end, "start-node", "end-node");
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.route_http_error", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.previously_failed", 1);
    }

    private List<Shape> createShapes(final Map<String, Stop> stops, final Map<String, JsonNode> nodes,
                                     final Trip trip) {
        when(splitterService.splitStoptimes(trip)).thenReturn(trip.stopTimes);
        return service.createShapesFromTrips(List.of(trip), stops, nodes, context);
    }

    private static Trip trip(final String tripId, final String... stopIds) {
        final Trip trip = new Trip(new Schedule());
        trip.tripId = tripId;
        trip.stopTimes = new ArrayList<>();
        for (final String stopId : stopIds) {
            trip.stopTimes.add(stopTime(stopId));
        }
        return trip;
    }

    private static Stop stop(final String id) {
        final Stop stop = new Stop(null);
        stop.stopId = id;
        stop.stopCode = id;
        stop.latitude = 60;
        stop.longitude = 24;
        return stop;
    }

    private static StopTime stopTime(final String id) {
        final StopTime stopTime = new StopTime(null);
        stopTime.stopId = id;
        return stopTime;
    }

    private static JsonNode node(final String id) {
        return MAPPER.readTree("[{\"tunniste\":\"" + id + "\"}]");
    }

    private void setField(final String name, final Object value) throws Exception {
        final Field field = GTFSShapeService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}