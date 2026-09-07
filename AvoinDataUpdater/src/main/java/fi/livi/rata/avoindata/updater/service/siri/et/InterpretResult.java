package fi.livi.rata.avoindata.updater.service.siri.et;

import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;

/**
 * Outcome of interpreting one {@link fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain}: either an
 * {@link Emitted} journey, or a {@link Skipped} with the reason (so the generation wide event can count
 * <em>why</em> journeys were dropped, not just that they were).
 */
public sealed interface InterpretResult permits InterpretResult.Emitted, InterpretResult.Skipped {

    enum SkipReason {
        /** No winning schedule — the train is not in the published timetable. */
        UNRESOLVED_JOURNEY,
        /** A commercial stop's station could not be mapped to a PETI stop place at all (station/PETI coverage gap). */
        UNRESOLVED_STOP_NO_STOP,
        /** A commercial stop's station has a PETI stop place, but no {@code FSR:Quay} for its platform/track. */
        UNRESOLVED_STOP_NO_QUAY,
        /** A previous operating day's train that has already finished (or gone stale) — not a current deviation. */
        COMPLETED_CARRYOVER
    }

    record Emitted(EtJourney journey) implements InterpretResult {}

    /**
     * A skipped journey, with the per-journey stop-resolution tally so the wide-event {@code match_rate} reflects
     * every commercial-stop lookup — including the quays a journey resolved before it was dropped at an
     * unresolved stop. {@code resolvedQuays} / {@code unresolvedStops} are 0 for journey-level skips
     * ({@code UNRESOLVED_JOURNEY}, {@code COMPLETED_CARRYOVER}).
     */
    record Skipped(SkipReason reason, int resolvedQuays, int unresolvedStops) implements InterpretResult {

        /** A journey-level skip that resolved no stops. */
        Skipped(final SkipReason reason) {
            this(reason, 0, 0);
        }
    }
}
