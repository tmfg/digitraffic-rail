package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsRunMetricsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-09-17T03:00:00Z"), ZoneOffset.UTC);

    private static GtfsRunMetrics metrics() {
        return new GtfsRunMetrics(CLOCK);
    }

    @Test
    void givenShapeProgressWhenEvery250ShapesAreProcessedThenAHeartbeatIsRecorded() {
        // Given
        final GtfsRunMetrics metrics = metrics();

        // When
        final List<Map<String, Object>> heartbeats = new ArrayList<>();
        for (int shape = 1; shape <= 500; shape++) {
            metrics.recordShapeProcessed("gtfs-all", shape, 500).ifPresent(heartbeats::add);
        }

        // Then
        assertThat(heartbeats).hasSize(2);
        assertThat(heartbeats.get(0))
                .containsEntry("operation", "generateGtfsFeed")
                .containsEntry("outcome", "in_progress")
                .containsEntry("rail.gtfs.feed.name", "gtfs-all")
                .containsEntry("rail.gtfs.shapes.processed", 250)
                .containsEntry("rail.gtfs.shapes.total", 500);
        assertThat(heartbeats.get(1))
                .containsEntry("rail.gtfs.shapes.processed", 500);
    }

    @Test
    void givenDummySegmentReasonsWhenRecordedThenEachReasonHasItsOwnCounter() {
        // Given
        final GtfsRunMetrics metrics = metrics();

        // When
        metrics.recordDummySegment(DummyReason.NO_START_NODE, "AAA");
        metrics.recordDummySegment(DummyReason.NO_END_NODE, "BBB");
        metrics.recordDummySegment(DummyReason.ROUTE_HTTP_ERROR, "CCC");
        metrics.recordDummySegment(DummyReason.ROUTE_EMPTY_GEOMETRY, "DDD");
        metrics.recordDummySegment(DummyReason.NO_DIJKSTRA_PATH, "EEE");

        // Then
        final Map<String, Object> event = metrics.finalEvent();
        assertThat(event)
                .containsEntry("rail.gtfs.segments.dummy.reason.no_start_node", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.no_end_node", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.route_http_error", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.route_empty_geometry", 1)
                .containsEntry("rail.gtfs.segments.dummy.reason.no_dijkstra_path", 1)
                .containsEntry("rail.gtfs.segments.dummy", 5)
                .containsEntry("rail.gtfs.segments.total", 5);
    }

    @Test
    void givenEveryDummyReasonWhenTheFinalEventIsBuiltThenEachOneHasACounter() {
        // Given
        final GtfsRunMetrics metrics = metrics();

        // When
        final Map<String, Object> event = metrics.finalEvent();

        // Then recording a reason and emitting it cannot drift apart
        for (final DummyReason reason : DummyReason.values()) {
            assertThat(event).containsKey("rail.gtfs.segments.dummy.reason." + reason.attribute());
        }
    }

    @Test
    void givenMoreThanTwentyDistinctRouteFailuresWhenRecordedThenSamplesAreCappedAndDuplicatesSuppressed() {
        // Given
        final GtfsRunMetrics metrics = metrics();
        for (int failure = 0; failure < 25; failure++) {
            metrics.recordRouteFailure(
                    "AAA" + failure + "->BBB",
                    "https://infra.example/reitit/" + failure,
                    503,
                    "ServiceUnavailable");
        }
        metrics.recordRouteFailure("AAA0->BBB", "https://infra.example/reitit/duplicate", 503, "ServiceUnavailable");

        // When
        final var samples = metrics.routeFailureSamples();

        // Then the duplicate identity does not consume a sample slot
        assertThat(samples).hasSize(20);
        assertThat(samples.getFirst())
                .containsKeys("url.full", "http.response.status_code", "error.type");
        assertThat(metrics.suppressedRouteFailures()).isEqualTo(5);
    }

    @Test
    void givenAnyRunOutcomeWhenFinalEventIsBuiltThenSuccessAndErrorUseTheSameWideFieldSet() {
        // Given
        final GtfsRunMetrics successful = metrics();
        final GtfsRunMetrics failed = metrics();
        failed.markError(new IllegalStateException("boom"));

        // When
        final Map<String, Object> success = successful.finalEvent();
        final Map<String, Object> error = failed.finalEvent();

        // Then
        assertThat(success.keySet()).containsExactlyInAnyOrderElementsOf(error.keySet());
        assertThat(success)
                .containsEntry("operation", "generateGtfs")
                .containsEntry("rail.entity.type", "gtfs_feed")
                .containsEntry("outcome", GtfsOutcome.SUCCESS.attribute())
                .containsEntry("error.type", "")
                .containsKeys("duration_ms", "rail.gtfs.feeds.attempted",
                        "rail.gtfs.feeds.published", "rail.gtfs.feeds.failed", "rail.gtfs.feeds.degraded");
        assertThat(error)
                .containsEntry("outcome", GtfsOutcome.ERROR.attribute())
                .containsEntry("error.type", "IllegalStateException");
    }

    @Test
    void givenFeedFailureWhenOutcomeIsDerivedThenRunIsPartial() {
        // Given a feed failure always propagates and marks the run, as production does
        final GtfsRunMetrics metrics = metrics();
        metrics.recordFeedAttempt("gtfs-all.zip");
        metrics.recordFeedPublished("gtfs-all.zip");
        metrics.recordFeedAttempt("gtfs-vr.zip");
        metrics.recordFeedFailed("gtfs-vr.zip");
        metrics.markError(new IllegalStateException("feed write failed"));

        // When
        final Map<String, Object> event = metrics.finalEvent();

        // Then
        assertThat(metrics.outcome()).isEqualTo(GtfsOutcome.PARTIAL);
        assertThat(event)
                .containsEntry("rail.gtfs.feeds.attempted", 2)
                .containsEntry("rail.gtfs.feeds.published", 1)
                .containsEntry("rail.gtfs.feeds.failed", 1)
                .containsEntry("rail.gtfs.feed.gtfs-vr.zip.failed", true)
                .containsEntry("rail.gtfs.feed.gtfs-all.zip.published", true);
    }

    @Test
    void givenFirstFeedFailsWhenOutcomeIsDerivedThenRunIsError() {
        // Given
        final GtfsRunMetrics metrics = metrics();
        metrics.recordFeedAttempt("gtfs-all.zip");
        metrics.recordFeedFailed("gtfs-all.zip");
        metrics.markError(new IllegalStateException("feed write failed"));

        // When / Then
        assertThat(metrics.outcome()).isEqualTo(GtfsOutcome.ERROR);
    }

    @Test
    void givenFallbackGeometryWhenOutcomeIsDerivedThenRunIsDegraded() {
        // Given
        final GtfsRunMetrics metrics = metrics();
        metrics.recordRealSegment();
        metrics.recordDummySegment(DummyReason.NO_START_NODE, "AAA");

        // When / Then
        assertThat(metrics.outcome()).isEqualTo(GtfsOutcome.DEGRADED);
    }

    @Test
    void givenDummySegmentsWhenTopStationsAreReportedThenTheyAreOrderedByFrequency() {
        // Given
        final GtfsRunMetrics metrics = metrics();
        metrics.recordDummySegment(DummyReason.NO_START_NODE, "HKI");
        metrics.recordDummySegment(DummyReason.NO_START_NODE, "HKI");
        metrics.recordDummySegment(DummyReason.NO_END_NODE, "TPE");

        // When
        final Map<String, Object> event = metrics.finalEvent();

        // Then
        assertThat(event).containsEntry("rail.gtfs.segments.dummy.stations.top", "HKI,TPE");
    }
}
