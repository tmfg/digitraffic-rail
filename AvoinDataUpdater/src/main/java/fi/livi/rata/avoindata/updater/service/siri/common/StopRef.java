package fi.livi.rata.avoindata.updater.service.siri.common;

/**
 * A resolved PETI stop reference put into a SIRI {@code StopPointRef} — always a {@code FSR:Quay}.
 */
public record StopRef(String value) {}
