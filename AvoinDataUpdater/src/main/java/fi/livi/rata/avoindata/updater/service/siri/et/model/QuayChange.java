package fi.livi.rata.avoindata.updater.service.siri.et.model;

import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;

/**
 * A platform (quay) change at one stop: the {@code aimed} (planned) quay differs from the {@code expected}
 * (actual/current) quay. Present on an {@link EtCall} only when a genuine change was detected; the marshaller
 * emits it as a SIRI {@code StopAssignment} ({@code AimedQuayRef} + {@code ExpectedQuayRef}). The call's
 * {@code StopPointRef} stays the {@code expected} quay.
 */
public record QuayChange(StopRef aimed, StopRef expected) {}
