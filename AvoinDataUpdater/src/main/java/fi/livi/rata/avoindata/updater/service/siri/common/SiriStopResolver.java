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

    public Optional<String> resolveQuayId(final int stationUicCode, final String commercialTrack) {
        final PetiUicMatcher matcher = getMatcher();
        final Optional<PetiStop> stop = matcher.match(stationUicCode);
        if (stop.isEmpty()) {
            return Optional.empty();
        }
        if (commercialTrack == null) {
            return Optional.of(stop.get().stopPlaceId());
        }
        return stop.get().resolveQuay(commercialTrack)
                .map(q -> q.quayId())
                .or(() -> Optional.of(stop.get().stopPlaceId()));
    }

    public Optional<String> resolveStopPlaceId(final int stationUicCode) {
        final PetiUicMatcher matcher = getMatcher();
        return matcher.match(stationUicCode).map(PetiStop::stopPlaceId);
    }
}
