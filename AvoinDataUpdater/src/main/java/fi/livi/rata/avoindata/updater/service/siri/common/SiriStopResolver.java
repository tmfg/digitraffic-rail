package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

/**
 * Resolves live station UIC codes to PETI {@code FSR:Quay} identifiers (falling back to {@code FSR:StopPlace}
 * when the track is unknown), against a {@link PetiUicMatcher} frozen for the generation cycle.
 */
public class SiriStopResolver {

    private final PetiUicMatcher matcher;

    public SiriStopResolver(final PetiUicMatcher matcher) {
        this.matcher = matcher;
    }

    public Optional<StopRef> resolveQuayId(final int stationUicCode, final String commercialTrack) {
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
}
