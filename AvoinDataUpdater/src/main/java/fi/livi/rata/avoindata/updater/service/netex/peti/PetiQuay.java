package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.math.BigDecimal;

/**
 * A quay (platform/track) within a PETI StopPlace.
 *
 * @param quayId        Verbatim NeTEx ID, e.g. "FSR:Quay:7"
 * @param publicCode    Track number as string, e.g. "1", "2"
 * @param latitude      Quay centroid latitude, or null when PETI omits it
 * @param longitude     Quay centroid longitude, or null when PETI omits it
 * @param accessibility Quay-level accessibility, or null if absent
 */
public record PetiQuay(
        String quayId,
        String publicCode,
        BigDecimal latitude,
        BigDecimal longitude,
        PetiAccessibility accessibility
) {

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
