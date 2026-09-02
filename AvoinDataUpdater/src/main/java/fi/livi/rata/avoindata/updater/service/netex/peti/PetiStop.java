package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.List;
import java.util.Optional;

/**
 * Parsed representation of a PETI NeTEx StopPlace.
 *
 * @param stopPlaceId     Verbatim NeTEx ID, e.g. "FSR:StopPlace:1"
 * @param uicCode         International UIC code (e.g. 1000361)
 * @param name            StopPlace name
 * @param parentStopPlace true if IS_PARENT_STOP_PLACE KeyValue is "true"
 * @param accessibility   StopPlace-level accessibility, or null if absent
 * @param quays           List of quays (never null, may be empty)
 */
public record PetiStop(
        String stopPlaceId,
        int uicCode,
        String name,
        boolean parentStopPlace,
        PetiAccessibility accessibility,
        List<PetiQuay> quays
) {

    /**
     * Find the quay whose publicCode matches the given commercial track.
     *
     * @param commercialTrack the track identifier (e.g. "1", "2")
     * @return matching quay, or empty if not found or commercialTrack is null
     */
    public Optional<PetiQuay> resolveQuay(final String commercialTrack) {
        if (commercialTrack == null) {
            return Optional.empty();
        }
        return quays.stream()
                .filter(q -> commercialTrack.equals(q.publicCode()))
                .findFirst();
    }
}
