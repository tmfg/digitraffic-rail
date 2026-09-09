package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SiriEtStats#from} — specifically that a journey skipped at an unresolved stop still
 * contributes the quays it resolved beforehand to the PETI match rate (review comment 3924820590).
 */
class SiriEtStatsTest {

    @Test
    void givenSkippedNoQuayWithPartialResolution_whenFrom_thenCountsResolvedAndUnresolved() {
        final SiriEtStats stats = SiriEtStats.from(List.of(
                new InterpretResult.Skipped(InterpretResult.SkipReason.UNRESOLVED_STOP_NO_QUAY, 3, 1)));

        assertEquals(3, stats.stopRefsQuay());
        assertEquals(1, stats.stopRefsUnresolved());
        assertEquals(1, stats.skippedUnresolvedStopNoQuay());
        assertEquals(0.75, stats.matchRate().orElseThrow(), 0.0001);
    }

    @Test
    void givenSkippedNoStopWithPartialResolution_whenFrom_thenCountsCarry() {
        final SiriEtStats stats = SiriEtStats.from(List.of(
                new InterpretResult.Skipped(InterpretResult.SkipReason.UNRESOLVED_STOP_NO_STOP, 2, 2)));

        assertEquals(2, stats.stopRefsQuay());
        assertEquals(2, stats.stopRefsUnresolved());
        assertEquals(0.5, stats.matchRate().orElseThrow(), 0.0001);
    }

    @Test
    void givenJourneyLevelAndCarryoverSkips_whenFrom_thenNoStopRefCounts() {
        final SiriEtStats stats = SiriEtStats.from(List.of(
                new InterpretResult.Skipped(InterpretResult.SkipReason.UNRESOLVED_JOURNEY),
                new InterpretResult.Skipped(InterpretResult.SkipReason.COMPLETED_CARRYOVER)));

        assertEquals(0, stats.stopRefsQuay());
        assertEquals(0, stats.stopRefsUnresolved());
        assertEquals(1, stats.skippedUnresolvedJourney());
        assertEquals(1, stats.skippedCompletedCarryover());
        assertEquals(OptionalDouble.empty(), stats.matchRate());
    }
}
