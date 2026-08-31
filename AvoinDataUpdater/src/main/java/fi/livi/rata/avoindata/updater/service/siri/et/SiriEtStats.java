package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.OptionalDouble;

/**
 * Per-cycle counters for the {@code rail.siri.et.generation} wide event: how many journeys were emitted,
 * cancelled or skipped (and why), the recorded/estimated call split, and the PETI stop-ref resolution
 * breakdown. A plain value object so the counts are unit-testable without scraping logs.
 */
public record SiriEtStats(
        long journeysEmitted,
        long journeysCancelled,
        long skippedUnresolvedJourney,
        long skippedUnresolvedStop,
        long callsRecorded,
        long callsEstimated,
        long stopRefsQuay,
        long stopRefsStopPlace,
        long stopRefsUnresolved) {

    public static SiriEtStats empty() {
        return new SiriEtStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public long callsTotal() {
        return callsRecorded + callsEstimated;
    }

    public long stopRefsResolved() {
        return stopRefsQuay + stopRefsStopPlace;
    }

    /** Stop-ref match rate (resolved / attempted); empty when no stop refs were attempted. */
    public OptionalDouble matchRate() {
        final long attempted = stopRefsResolved() + stopRefsUnresolved;
        return attempted == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) stopRefsResolved() / attempted);
    }
}
