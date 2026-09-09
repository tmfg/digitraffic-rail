package fi.livi.rata.avoindata.updater.service.siri.et.model;

import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;

/**
 * One stop of an {@link EtJourney}. A stop is exactly one of two shapes, mirroring the SIRI-ET schema
 * {@code choice}: a {@link Recorded} call (already served — carries {@code Actual*Time}) or an
 * {@link Estimated} call (upcoming — carries {@code Expected*Time}).
 *
 * <p>{@code arrival} is {@code null} at the origin and {@code departure} is {@code null} at the terminus.
 * {@code cancelled} means the whole stop is cancelled ({@code Cancellation=true}); a call may still carry a
 * per-side {@link CallStatus#CANCELLED} status without being cancelled itself (the partial-cancellation
 * boundary: the last served stop before a cancelled one departs {@code cancelled}).
 *
 * <p>{@code stopName} is the station name for {@code StopPointName} (null when unavailable). {@code quayChange}
 * is present only on a genuine platform change and becomes a SIRI {@code StopAssignment}.
 */
public sealed interface EtCall permits EtCall.Recorded, EtCall.Estimated {

    StopRef stopRef();

    int order();

    boolean cancelled();

    CallPoint arrival();

    CallPoint departure();

    String stopName();

    QuayChange quayChange();

    record Recorded(StopRef stopRef, int order, boolean cancelled, CallPoint arrival, CallPoint departure,
                    String stopName, QuayChange quayChange) implements EtCall {}

    record Estimated(StopRef stopRef, int order, boolean cancelled, CallPoint arrival, CallPoint departure,
                     boolean predictionInaccurate, String stopName, QuayChange quayChange) implements EtCall {}
}
