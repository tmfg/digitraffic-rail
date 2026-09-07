package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.apache.commons.lang3.BooleanUtils;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.StopRef;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule.Leg;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallPoint;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallStatus;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtCall;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;
import fi.livi.rata.avoindata.updater.service.siri.et.model.QuayChange;

/**
 * Interprets a live {@link GTFSTrain} into the domain {@link EtJourney} IR: it decides <em>what the
 * real-time situation is</em> (journey ref, per-stop recorded/estimated, delay status, cancellation
 * boundary, quay ref) without touching any SIRI/JAXB type. Marshalling is a separate concern
 * ({@link EtJourneyMarshaller}).
 *
 * <p>Returns an {@link InterpretResult.Skipped} when the journey does not resolve to a published
 * {@code ServiceJourney} ({@code UNRESOLVED_JOURNEY}), or when any commercial stop cannot be resolved to a PETI
 * {@code FSR:Quay} ({@code UNRESOLVED_STOP_NO_STOP} / {@code UNRESOLVED_STOP_NO_QUAY}) — because the profile
 * requires a complete stop sequence, an incomplete journey is skipped rather than emitted.
 */
public class EtJourneyInterpreter {

    // Deviations within a minute are treated as on-time.
    private static final long ON_TIME_TOLERANCE_SECONDS = 60;

    // How long after its final stop's expected time a carried-over previous-day journey is still emitted.
    private static final Duration CARRYOVER_GRACE = Duration.ofMinutes(15);

    private final JourneyRefResolver journeyRefResolver;
    private final StationUicLookup stationUicLookup;
    private final SiriStopResolver siriStopResolver;
    private final StationNameLookup stationNameLookup;
    private final PlannedTrackLookup plannedTrackLookup;

    public EtJourneyInterpreter(final JourneyRefResolver journeyRefResolver,
                                final StationUicLookup stationUicLookup,
                                final SiriStopResolver siriStopResolver,
                                final StationNameLookup stationNameLookup,
                                final PlannedTrackLookup plannedTrackLookup) {
        this.journeyRefResolver = journeyRefResolver;
        this.stationUicLookup = stationUicLookup;
        this.siriStopResolver = siriStopResolver;
        this.stationNameLookup = stationNameLookup;
        this.plannedTrackLookup = plannedTrackLookup;
    }

    public InterpretResult interpret(final GTFSTrain train, final ZonedDateTime now) {
        final Optional<ResolvedJourney> resolved =
                journeyRefResolver.resolve(train.id.trainNumber, train.id.departureDate);
        if (resolved.isEmpty()) {
            return new InterpretResult.Skipped(InterpretResult.SkipReason.UNRESOLVED_JOURNEY);
        }

        final List<PairedStop> commercialStops = new ArrayList<>();
        for (final PairedStop stop : pairRows(train.timeTableRows)) {
            if (isCommercial(stop)) {
                commercialStops.add(stop);
            }
        }

        // A previous operating day's train only belongs in today's ET while it is genuinely still running; a
        // completed or stale carryover is dropped rather than re-published every cycle.
        if (train.id.departureDate.isBefore(now.toLocalDate()) && !isStillRunning(commercialStops, now)) {
            return new InterpretResult.Skipped(InterpretResult.SkipReason.COMPLETED_CARRYOVER);
        }

        final List<ResolvedStop> stops = new ArrayList<>(commercialStops.size());
        boolean monitored = false;
        for (final PairedStop stop : commercialStops) {
            final Optional<StopRef> stopRef = resolveStopRef(stop);
            // The Nordic profile requires us to assert IsCompleteStopSequence=true. If even one commercial stop can't
            // be resolved to a Quay, we can't honestly claim a complete sequence, so we omit the entire journey rather
            // than publish a hole. The reason (no PETI stop vs no quay) is recorded so operators can see which.
            if (stopRef.isEmpty()) {
                return new InterpretResult.Skipped(unresolvedStopReason(stop));
            }
            final String stopName = stationNameLookup.nameFor(representativeRow(stop).stationShortCode).orElse(null);
            stops.add(new ResolvedStop(stop, stopRef.get(), stopName));
            // If any stop has live data, the whole journey is considered monitored.
            monitored = monitored || hasLiveData(stop);
        }

        final List<EtCall> calls = new ArrayList<>(stops.size());
        // The train's furthest realised call: every stop at or before it is in the past (a RecordedCall), even
        // one with no actual time of its own — such a stop is a "missed"/not-yet-recorded call carrying the
        // estimate. Everything after is still upcoming. This keeps RecordedCalls the chronological prefix and
        // EstimatedCalls the suffix, instead of interleaving them by per-stop actual presence.
        int lastActualIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (hasAnyActualTime(stops.get(i).stop())) {
                lastActualIndex = i;
            }
        }
        for (int i = 0; i < stops.size(); i++) {
            final ResolvedStop current = stops.get(i);
            final int order = i + 1;
            // Partial-cancellation boundary: the last served stop before a cancelled stop departs 'cancelled'.
            final boolean nextCancelled = i + 1 < stops.size() && isCancelled(stops.get(i + 1).stop());
            calls.add(toCall(train, current.stop(), current.stopRef(), current.stopName(), order, nextCancelled,
                    i <= lastActualIndex));
        }

        final ResolvedJourney j = resolved.get();
        return new InterpretResult.Emitted(new EtJourney(
                j.serviceJourneyId(), j.dataFrameRef(), j.lineId(), j.operatorRef(), j.journeyPatternRef(),
                train.cancelled, monitored,
                stops.isEmpty() ? null : stops.get(0).stopName(),
                stops.isEmpty() ? null : stops.get(stops.size() - 1).stopName(),
                calls));
    }

    private EtCall toCall(final GTFSTrain train, final PairedStop stop, final StopRef stopRef, final String stopName,
                          final int order, final boolean nextCancelled, final boolean past) {
        final boolean cancelled = isCancelled(stop);
        // Whether this stop has a realised time of its own. A past stop that has none is a "missed" call: it is
        // still a RecordedCall (behind the train's furthest actual) but carries the estimate as Expected*Time.
        final boolean hasActual = hasAnyActualTime(stop);

        final CallPoint arrival = stop.arrival == null ? null
                : new CallPoint(
                        stop.arrival.scheduledTime,
                        hasActual ? null : stop.arrival.liveEstimateTime,
                        hasActual ? stop.arrival.actualTime : null,
                        cancelled ? CallStatus.CANCELLED : timeStatus(stop.arrival));

        final CallStatus departureStatus = (cancelled || nextCancelled) ? CallStatus.CANCELLED
                : (stop.departure == null ? null : timeStatus(stop.departure));
        final CallPoint departure = stop.departure == null ? null
                : new CallPoint(
                        stop.departure.scheduledTime,
                        stop.departure.liveEstimateTime,
                        hasActual ? stop.departure.actualTime : null,
                        departureStatus);

        final QuayChange quayChange = computeQuayChange(train, stop, stopRef);

        if (past) {
            return new EtCall.Recorded(stopRef, order, cancelled, arrival, departure, stopName, quayChange);
        }
        return new EtCall.Estimated(stopRef, order, cancelled, arrival, departure,
                isPredictionInaccurate(stop), stopName, quayChange);
    }

    /**
     * Detects a platform change: returns a {@link QuayChange} only when the planned track is known, the current
     * platform is known, and the planned quay genuinely differs from the actual quay. When the current platform
     * is unknown we do not assert a change (such a stop has no resolvable quay and its journey is dropped upstream).
     */
    private QuayChange computeQuayChange(final GTFSTrain train, final PairedStop stop, final StopRef actualQuay) {
        final GTFSTimeTableRow representative = representativeRow(stop);
        final Optional<String> plannedTrack = plannedTrackLookup.plannedTrack(
                train.id.trainNumber, train.id.departureDate, representative.stationShortCode);
        // Need a planned track to resolve the planned quay; a null actual track means the current platform is
        // unknown, so the stop has no resolvable quay and never carries a change.
        if (plannedTrack.isEmpty() || actualTrackOf(representative) == null) {
            return null;
        }
        final OptionalInt uic = stationUicLookup.uicFor(representative.stationShortCode);
        if (uic.isEmpty()) {
            return null;
        }
        final Optional<StopRef> plannedQuay = siriStopResolver.resolveQuayId(uic.getAsInt(), plannedTrack.get());
        if (plannedQuay.isEmpty() || plannedQuay.get().equals(actualQuay)) {
            return null;
        }
        return new QuayChange(plannedQuay.get(), actualQuay);
    }

    private Optional<StopRef> resolveStopRef(final PairedStop stop) {
        final GTFSTimeTableRow representative = representativeRow(stop);
        final OptionalInt uic = stationUicLookup.uicFor(representative.stationShortCode);
        if (uic.isEmpty()) {
            return Optional.empty();
        }
        return siriStopResolver.resolveQuayId(uic.getAsInt(), actualTrackOf(representative));
    }

    /** Why a commercial stop didn't resolve: no PETI stop place for the station, or a stop place but no quay. */
    private InterpretResult.SkipReason unresolvedStopReason(final PairedStop stop) {
        final GTFSTimeTableRow representative = representativeRow(stop);
        final OptionalInt uic = stationUicLookup.uicFor(representative.stationShortCode);
        if (uic.isEmpty() || !siriStopResolver.hasStopPlace(uic.getAsInt())) {
            return InterpretResult.SkipReason.UNRESOLVED_STOP_NO_STOP;
        }
        return InterpretResult.SkipReason.UNRESOLVED_STOP_NO_QUAY;
    }

    private static GTFSTimeTableRow representativeRow(final PairedStop stop) {
        return stop.arrival != null ? stop.arrival : stop.departure;
    }

    private static String actualTrackOf(final GTFSTimeTableRow row) {
        return BooleanUtils.isTrue(row.unknownTrack) ? null : row.commercialTrack;
    }

    private static CallStatus timeStatus(final GTFSTimeTableRow row) {
        final long delaySeconds = row.delayInSeconds();
        if (delaySeconds >= ON_TIME_TOLERANCE_SECONDS) {
            return CallStatus.DELAYED;
        }
        if (delaySeconds <= -ON_TIME_TOLERANCE_SECONDS) {
            return CallStatus.EARLY;
        }
        return CallStatus.ON_TIME;
    }

    private static boolean hasAnyActualTime(final PairedStop stop) {
        if (stop.arrival != null && stop.arrival.actualTime != null) {
            return true;
        }
        return stop.departure != null && stop.departure.actualTime != null;
    }

    private static boolean isCommercial(final PairedStop stop) {
        // Must select the same stops as the NeTEx timetable — see CommercialStopRule.
        return CommercialStopRule.isCommercialStop(leg(stop.arrival), leg(stop.departure));
    }

    private static Leg leg(final GTFSTimeTableRow row) {
        if (row == null) {
            return Leg.ABSENT;
        }
        return BooleanUtils.isTrue(row.commercialStop) ? Leg.COMMERCIAL : Leg.NON_COMMERCIAL;
    }

    private static boolean isCancelled(final PairedStop stop) {
        if (stop.arrival != null && stop.arrival.cancelled) {
            return true;
        }
        return stop.departure != null && stop.departure.cancelled;
    }

    private static boolean isPredictionInaccurate(final PairedStop stop) {
        return (stop.arrival != null && BooleanUtils.isTrue(stop.arrival.unknownDelay))
                || (stop.departure != null && BooleanUtils.isTrue(stop.departure.unknownDelay));
    }

    private static boolean hasLiveData(final PairedStop stop) {
        return (stop.arrival != null && stop.arrival.hasEstimateOrActualTime())
                || (stop.departure != null && stop.departure.hasEstimateOrActualTime());
    }

    /**
     * A carried-over previous-day journey is still running until its final commercial stop is served. A small
     * grace window keeps a just-arrived train briefly, and drops journeys whose data went stale without ever
     * completing (final stop never got an actual time and its expected time is well in the past).
     */
    private static boolean isStillRunning(final List<PairedStop> commercialStops, final ZonedDateTime now) {
        if (commercialStops.isEmpty()) {
            return false;
        }
        final PairedStop last = commercialStops.get(commercialStops.size() - 1);
        final GTFSTimeTableRow lastRow = last.arrival != null ? last.arrival : last.departure;
        if (lastRow.actualTime != null) {
            return false;
        }
        final ZonedDateTime effective = lastRow.liveEstimateTime != null ? lastRow.liveEstimateTime
                : lastRow.scheduledTime;
        return effective != null && !effective.isBefore(now.minus(CARRYOVER_GRACE));
    }

    /**
     * Pairs time table rows into stops: origin (DEPARTURE only), middle (ARRIVAL+DEPARTURE), terminus
     * (ARRIVAL only). The {@code timeTableRows} association declares no order, so the rows are first sorted
     * (into a copy) by scheduled time, then ARRIVAL before DEPARTURE — the same key used when they are ingested.
     */
    private static List<PairedStop> pairRows(final List<GTFSTimeTableRow> rows) {
        final List<PairedStop> stops = new ArrayList<>();
        if (rows.isEmpty()) {
            return stops;
        }

        final List<GTFSTimeTableRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing((GTFSTimeTableRow r) -> r.scheduledTime).thenComparing(r -> r.type));

        int i = 0;
        if (ordered.get(0).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
            stops.add(new PairedStop(null, ordered.get(0)));
            i = 1;
        }

        while (i < ordered.size()) {
            final GTFSTimeTableRow arrival = ordered.get(i);
            i++;
            if (i < ordered.size() && ordered.get(i).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
                stops.add(new PairedStop(arrival, ordered.get(i)));
                i++;
            } else {
                stops.add(new PairedStop(arrival, null));
            }
        }

        return stops;
    }

    private record PairedStop(GTFSTimeTableRow arrival, GTFSTimeTableRow departure) {}

    /** A commercial stop paired with its resolved PETI stop ref and station name, carried as one unit. */
    private record ResolvedStop(PairedStop stop, StopRef stopRef, String stopName) {}
}
