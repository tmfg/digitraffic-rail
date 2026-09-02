package fi.livi.rata.avoindata.updater.service.netex.peti;

/**
 * Tri-state accessibility limitation status, mirroring NeTEx LimitationStatusEnumeration
 * but decoupled from the JAXB model for the PETI data layer.
 */
public enum PetiLimitationStatus {
    TRUE,
    FALSE,
    UNKNOWN
}
