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
        /** A commercial stop could not be resolved to a PETI quay, so the incomplete journey is dropped. */
        UNRESOLVED_STOP
    }

    record Emitted(EtJourney journey) implements InterpretResult {}

    record Skipped(SkipReason reason) implements InterpretResult {}
}
