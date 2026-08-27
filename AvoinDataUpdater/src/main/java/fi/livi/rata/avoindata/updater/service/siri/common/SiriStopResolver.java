package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

/**
 * Resolves live station UIC codes to PETI FSR:Quay or FSR:StopPlace identifiers.
 */
public class SiriStopResolver {

    private final PetiStopSource petiStopSource;
    private final PetiUicMatcher prebuiltMatcher;

    public SiriStopResolver(final PetiStopSource petiStopSource) {
        this.petiStopSource = petiStopSource;
        this.prebuiltMatcher = null;
    }

    public SiriStopResolver(final PetiUicMatcher matcher) {
        this.petiStopSource = null;
        this.prebuiltMatcher = matcher;
    }

    private PetiUicMatcher getMatcher() {
        return prebuiltMatcher != null ? prebuiltMatcher : petiStopSource.getMatcher();
    }

    public Optional<StopRef> resolveQuayId(final int stationUicCode, final String commercialTrack) {
        final PetiUicMatcher matcher = getMatcher();
        final Optional<PetiStop> stop = matcher.match(stationUicCode);
        if (stop.isEmpty()) {
            return Optional.empty();
        }
        if (commercialTrack == null) {
            return Optional.of(new StopRef(stop.get().stopPlaceId()));
        }
        return stop.get().resolveQuay(commercialTrack)
                .map(q -> new StopRef(q.quayId()))
                .or(() -> Optional.of(new StopRef(stop.get().stopPlaceId())));
    }

    public Optional<StopRef> resolveStopPlaceId(final int stationUicCode) {
        final PetiUicMatcher matcher = getMatcher();
        return matcher.match(stationUicCode).map(stop -> new StopRef(stop.stopPlaceId()));
    }
}
