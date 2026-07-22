package fi.livi.rata.avoindata.updater.service.netex.peti;

/**
 * A quay (platform/track) within a PETI StopPlace.
 *
 * @param quayId       Verbatim NeTEx ID, e.g. "FSR:Quay:7"
 * @param publicCode   Track number as string, e.g. "1", "2"
 * @param accessibility Quay-level accessibility, or null if absent
 */
public record PetiQuay(
        String quayId,
        String publicCode,
        PetiAccessibility accessibility
) {
}
