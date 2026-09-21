package fi.livi.rata.avoindata.updater.service.gtfs;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GTFSShapeServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final LocalDate ROUTE_DATE = LocalDate.of(2026, 9, 21);

    private GTFSShapeService service;
    private TrakediaRouteService routeService;
    private StoptimesSplitterService splitterService;
    private GtfsRunMetrics metrics;
    private FailedSegments failedSegments;

    @BeforeEach
    void setUp() throws Exception {
        service = new GTFSShapeService();
        routeService = mock(TrakediaRouteService.class);
        splitterService = mock(StoptimesSplitterService.class);
        metrics = new GtfsRunMetrics(Clock.systemUTC());
        failedSegments = new FailedSegments();
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
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
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
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
                .thenReturn(Optional.of(List.of(new Coordinate(1, 1), new Coordinate(2, 2))));

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
    void givenUnresolvedRouteWhenShapesAreCreatedThenThisServiceAttributesNoReason() throws Exception {
        // Given a response the route service already attributed as empty geometry or no path
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
                .thenReturn(Optional.empty());

        // When
        createShapes(Map.of("AAA", start, "BBB", end),
                Map.of("AAA", node("start-node"), "BBB", node("end-node")), trip);

        // Then the segment totals are recorded here, but the reason is not counted twice
        final Map<String, Object> event = metrics.finalEvent();
        assertThat(event).containsEntry("rail.gtfs.segments.dummy", 1);
        assertThat(attributedReasons(event)).isZero();
        assertThat(metrics.routeFailureSamples()).isEmpty();
    }

    @Test
    void givenReasonsOwnedByThisServiceWhenShapesAreCreatedThenEachDummySegmentIsAttributedOnce() throws Exception {
        // Given one segment with no node and one whose route request fails
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
                .thenThrow(WebClientResponseException.create(503, "unavailable", null, null, null));
        createShapes(Map.of("AAA", start, "BBB", end), Map.of(), trip("trip-1", "AAA", "BBB"));

        // When
        createShapes(Map.of("AAA", start, "BBB", end),
                Map.of("AAA", node("start-node"), "BBB", node("end-node")), trip("trip-2", "AAA", "BBB"));

        // Then
        final Map<String, Object> event = metrics.finalEvent();
        assertThat(event).containsEntry("rail.gtfs.segments.dummy", 2);
        assertThat(attributedReasons(event)).isEqualTo(2);
    }

    @Test
    void givenFallbackGeometryWhenShapesAreCreatedThenTheFeedIsMarkedDegraded() throws Exception {
        // Given
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        final Trip trip = trip("trip-1", "AAA", "BBB");
        metrics.recordFeedAttempt("gtfs-all.zip");

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
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
                .thenThrow(WebClientResponseException.create(503, "unavailable", null, null, null));
        final Map<String, Stop> stops = Map.of("AAA", start, "BBB", end);
        final Map<String, JsonNode> nodes = Map.of("AAA", node("start-node"), "BBB", node("end-node"));
        createShapes(stops, nodes, trip("trip-1", "AAA", "BBB"));

        // When the same pair appears again in a later feed
        createShapes(stops, nodes, trip("trip-2", "AAA", "BBB"));

        // Then only the first occurrence issued a request
        verify(routeService, times(1)).createRoute(start, end, "start-node", "end-node", ROUTE_DATE);
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.route_http_error", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.previously_failed", 1);
    }

    @Test
    void givenUnresolvedRouteWhenASecondFeedRunsThenTheRouteIsNotRequestedAgain() throws Exception {
        // Given a successful response that carries no usable geometry
        final Stop start = stop("AAA");
        final Stop end = stop("BBB");
        when(routeService.createRoute(start, end, "start-node", "end-node", ROUTE_DATE))
                .thenReturn(Optional.empty());
        final Map<String, Stop> stops = Map.of("AAA", start, "BBB", end);
        final Map<String, JsonNode> nodes = Map.of("AAA", node("start-node"), "BBB", node("end-node"));
        createShapes(stops, nodes, trip("trip-1", "AAA", "BBB"));

        // When the same pair appears again in a later feed
        createShapes(stops, nodes, trip("trip-2", "AAA", "BBB"));

        // Then the run-scoped failed-segment set suppresses it, not the shared cache
        verify(routeService, times(1)).createRoute(start, end, "start-node", "end-node", ROUTE_DATE);
        assertThat(metrics.finalEvent())
                .containsEntry("rail.gtfs.segments.dummy.reason.previously_failed", 1);
    }

    @Test
    void givenTripsSharingShapesWhenProgressIsReportedThenTheDenominatorCountsGeometryWork() {
        // Given 250 distinct stop sequences, each used by two trips
        final Map<String, Stop> stops = new HashMap<>();
        final List<Trip> trips = new ArrayList<>();
        for (int shape = 0; shape < 250; shape++) {
            final String start = "S" + shape;
            final String end = "E" + shape;
            stops.put(start, stop(start));
            stops.put(end, stop(end));
            trips.add(trip("trip-a-" + shape, start, end));
            trips.add(trip("trip-b-" + shape, start, end));
        }
        when(splitterService.splitStoptimes(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> ((Trip) invocation.getArgument(0)).stopTimes);
        final GtfsRunMetrics recorder = org.mockito.Mockito.spy(metrics);

        // When
        GtfsRunScope.run(recorder,
                () -> service.createShapesFromTrips(trips, stops, Map.of(), ROUTE_DATE, failedSegments));

        // Then progress and total are in the same unit, so a heartbeat can reach its total
        verify(recorder, times(250)).recordShapeProcessed(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(250));
    }

    @Test
    void givenNoBoundRunWhenShapesAreCreatedThenTheMissingScopeFailsLoudly() {
        // Given a caller that did not bind the run
        final Trip trip = trip("trip-1", "AAA", "BBB");
        when(splitterService.splitStoptimes(trip)).thenReturn(trip.stopTimes);

        // When / Then counters are never silently discarded
        assertThatThrownBy(() -> service.createShapesFromTrips(List.of(trip),
                Map.of("AAA", stop("AAA"), "BBB", stop("BBB")), Map.of(), ROUTE_DATE, failedSegments))
                .isInstanceOf(NoSuchElementException.class);
    }

    /** Guards the ownership split: every dummy segment carries exactly one reason. */
    private static int attributedReasons(final Map<String, Object> event) {
        int total = 0;
        for (final NoGeometryReason reason : NoGeometryReason.values()) {
            total += (Integer) event.get("rail.gtfs.segments.dummy.reason." + reason.attribute());
        }
        return total;
    }

    private List<Shape> createShapes(final Map<String, Stop> stops, final Map<String, JsonNode> nodes,
                                     final Trip trip) {
        when(splitterService.splitStoptimes(trip)).thenReturn(trip.stopTimes);
        return GtfsRunScope.call(metrics,
                () -> service.createShapesFromTrips(List.of(trip), stops, nodes, ROUTE_DATE, failedSegments));
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
