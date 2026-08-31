package fi.livi.rata.avoindata.updater.service.siri.et;

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
 * {@code FSR:Quay} ({@code UNRESOLVED_STOP}) — because the profile requires a complete stop sequence, an
 * incomplete journey is skipped rather than emitted.
 */
public class EtJourneyInterpreter {

    // Deviations within a minute are treated as on-time.
    private static final long ON_TIME_TOLERANCE_SECONDS = 60;

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

    public InterpretResult interpret(final GTFSTrain train) {
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

        final List<StopRef> stopRefs = new ArrayList<>(commercialStops.size());
        final List<String> stopNames = new ArrayList<>(commercialStops.size());
        boolean monitored = false;
        for (final PairedStop stop : commercialStops) {
            final Optional<StopRef> stopRef = resolveStopRef(stop);
            // The Nordic profile requires us to assert IsCompleteStopSequence=true. If even one commercial stop can't
            // be resolved to a Quay, we can't honestly claim a complete sequence, so we omit the entire journey rather
            // than publish a hole.
            if (stopRef.isEmpty()) {
                return new InterpretResult.Skipped(InterpretResult.SkipReason.UNRESOLVED_STOP);
            }
            stopRefs.add(stopRef.get());
            stopNames.add(stationNameLookup.nameFor(representativeRow(stop).stationShortCode).orElse(null));
            // If any stop has live data, the whole journey is considered monitored.
            monitored = monitored || hasLiveData(stop);
        }

        final List<EtCall> calls = new ArrayList<>(commercialStops.size());
        for (int i = 0; i < commercialStops.size(); i++) {
            final PairedStop stop = commercialStops.get(i);
            final int order = i + 1;
            // Partial-cancellation boundary: the last served stop before a cancelled stop departs 'cancelled'.
            final boolean nextCancelled = i + 1 < commercialStops.size() && isCancelled(commercialStops.get(i + 1));
            calls.add(toCall(train, stop, stopRefs.get(i), stopNames.get(i), order, nextCancelled));
        }

        final ResolvedJourney j = resolved.get();
        return new InterpretResult.Emitted(new EtJourney(
                j.serviceJourneyId(), j.dataFrameRef(), j.lineId(), j.operatorRef(),
                train.cancelled, monitored,
                stopNames.isEmpty() ? null : stopNames.get(0),
                stopNames.isEmpty() ? null : stopNames.get(stopNames.size() - 1),
                calls));
    }

    private EtCall toCall(final GTFSTrain train, final PairedStop stop, final StopRef stopRef, final String stopName,
                          final int order, final boolean nextCancelled) {
        final boolean cancelled = isCancelled(stop);
        final boolean recorded = hasAnyActualTime(stop);

        final CallPoint arrival = stop.arrival == null ? null
                : new CallPoint(
                        stop.arrival.scheduledTime,
                        recorded ? null : stop.arrival.liveEstimateTime,
                        recorded ? stop.arrival.actualTime : null,
                        cancelled ? CallStatus.CANCELLED : timeStatus(stop.arrival));

        final CallStatus departureStatus = (cancelled || nextCancelled) ? CallStatus.CANCELLED
                : (stop.departure == null ? null : timeStatus(stop.departure));
        final CallPoint departure = stop.departure == null ? null
                : new CallPoint(
                        stop.departure.scheduledTime,
                        stop.departure.liveEstimateTime,
                        recorded ? stop.departure.actualTime : null,
                        departureStatus);

        final QuayChange quayChange = computeQuayChange(train, stop, stopRef);

        if (recorded) {
            return new EtCall.Recorded(stopRef, order, cancelled, arrival, departure, stopName, quayChange);
        }
        return new EtCall.Estimated(stopRef, order, cancelled, arrival, departure,
                isPredictionInaccurate(stop), stopName, quayChange);
    }

    /**
     * Detects a platform change: returns a {@link QuayChange} only when the planned track is known, the current
     * platform is known, and the planned quay genuinely differs from the actual quay. When the current platform
     * is unknown ({@code actualQuay} is only a {@code StopPlace} fallback) we do not assert a change.
     */
    private QuayChange computeQuayChange(final GTFSTrain train, final PairedStop stop, final StopRef actualQuay) {
        final GTFSTimeTableRow representative = representativeRow(stop);
        final Optional<String> plannedTrack = plannedTrackLookup.plannedTrack(
                train.id.trainNumber, train.id.departureDate, representative.stationShortCode);
        // Need a planned track to resolve the planned quay; a null actual track means the current platform is
        // unknown, so actualQuay is only a StopPlace fallback and we must not assert a change.
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
}
