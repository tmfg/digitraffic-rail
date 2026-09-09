package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;

/**
 * Resolves live station UIC codes + tracks to PETI {@code FSR:Quay} identifiers, against a
 * {@link PetiUicMatcher} frozen for the generation cycle. Returns empty when the platform/track cannot be
 * resolved to a Quay
 */
public class SiriStopResolver {

    private final PetiUicMatcher matcher;

    public SiriStopResolver(final PetiUicMatcher matcher) {
        this.matcher = matcher;
    }

    public Optional<StopRef> resolveQuayId(final int stationUicCode, final String commercialTrack) {
        if (commercialTrack == null) {
            return Optional.empty();
        }
        return matcher.match(stationUicCode)
                .flatMap(stop -> stop.resolveQuay(commercialTrack))
                .map(quay -> new StopRef(quay.quayId()));
    }

    /** Whether PETI has a stop place for this station UIC (used to tell "no stop" apart from "no quay"). */
    public boolean hasStopPlace(final int stationUicCode) {
        return matcher.hasStopPlace(stationUicCode);
    }
}
