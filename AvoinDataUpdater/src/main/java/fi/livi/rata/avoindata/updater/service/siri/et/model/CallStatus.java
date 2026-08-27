package fi.livi.rata.avoindata.updater.service.siri.et.model;

/** Deviation status of one call side (arrival or departure), independent of the SIRI enum. */
public enum CallStatus {
    ON_TIME,
    DELAYED,
    EARLY,
    CANCELLED
}
