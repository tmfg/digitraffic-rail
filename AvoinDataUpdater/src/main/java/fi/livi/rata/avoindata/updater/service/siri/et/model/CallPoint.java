package fi.livi.rata.avoindata.updater.service.siri.et.model;

import java.time.ZonedDateTime;

/**
 * One side (arrival or departure) of a call: the three SIRI times as raw instants plus the derived
 * {@link CallStatus}. Any of the times may be {@code null} when not applicable.
 */
public record CallPoint(ZonedDateTime aimed, ZonedDateTime expected, ZonedDateTime actual, CallStatus status) {}
