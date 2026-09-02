package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.List;
import java.util.OptionalDouble;

import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtCall;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;

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

    /** Folds one cycle's interpretation results into the counters — the sole place these tallies live. */
    public static SiriEtStats from(final List<InterpretResult> results) {
        long emitted = 0;
        long cancelled = 0;
        long skippedUnresolvedJourney = 0;
        long skippedUnresolvedStop = 0;
        long callsRecorded = 0;
        long callsEstimated = 0;
        long stopRefsQuay = 0;
        long stopRefsStopPlace = 0;
        long stopRefsUnresolved = 0;

        for (final InterpretResult result : results) {
            switch (result) {
                case InterpretResult.Skipped skipped -> {
                    switch (skipped.reason()) {
                        case UNRESOLVED_JOURNEY -> skippedUnresolvedJourney++;
                        case UNRESOLVED_STOP -> {
                            skippedUnresolvedStop++;
                            stopRefsUnresolved++;
                        }
                    }
                }
                case InterpretResult.Emitted emittedResult -> {
                    final EtJourney journey = emittedResult.journey();
                    emitted++;
                    if (journey.cancelled()) {
                        cancelled++;
                    }
                    for (final EtCall call : journey.calls()) {
                        if (call instanceof EtCall.Recorded) {
                            callsRecorded++;
                        } else {
                            callsEstimated++;
                        }
                        if (isQuay(call.stopRef())) {
                            stopRefsQuay++;
                        } else {
                            stopRefsStopPlace++;
                        }
                    }
                }
            }
        }

        return new SiriEtStats(emitted, cancelled, skippedUnresolvedJourney, skippedUnresolvedStop,
                callsRecorded, callsEstimated, stopRefsQuay, stopRefsStopPlace, stopRefsUnresolved);
    }

    // FSR:Quay:... when the track resolved, FSR:StopPlace:... on the track-unknown fallback.
    private static boolean isQuay(final StopRef stopRef) {
        return stopRef.value().contains(":Quay:");
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
