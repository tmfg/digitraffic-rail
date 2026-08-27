package fi.livi.rata.avoindata.updater.service.siri.common;

/**
 * A resolved PETI stop reference put into a SIRI {@code StopPointRef} — a {@code FSR:Quay} when the track is
 * known, otherwise the {@code FSR:StopPlace} fallback.
 */
public record StopRef(String value) {}
