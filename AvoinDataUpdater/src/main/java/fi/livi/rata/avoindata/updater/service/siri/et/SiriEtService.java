package fi.livi.rata.avoindata.updater.service.siri.et;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import org.apache.commons.lang3.BooleanUtils;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DataFrameRefStructure;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.DirectionRefStructure;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

/**
 * Builds a SIRI-ET ServiceDelivery document from live GTFSTrains.
 */
public class SiriEtService {

    private final JourneyRefResolver journeyRefResolver;
    private final StationUicLookup stationUicLookup;
    private final SiriStopResolver siriStopResolver;
    private final SiriWritingService siriWritingService;
    private final String producerRef;
    private final String dataSource;

    public SiriEtService(
            final JourneyRefResolver journeyRefResolver,
            final StationUicLookup stationUicLookup,
            final SiriStopResolver siriStopResolver,
            final SiriWritingService siriWritingService,
            final String producerRef,
            final String dataSource) {
        this.journeyRefResolver = journeyRefResolver;
        this.stationUicLookup = stationUicLookup;
        this.siriStopResolver = siriStopResolver;
        this.siriWritingService = siriWritingService;
        this.producerRef = producerRef;
        this.dataSource = dataSource;
    }

    public Siri buildEtDocument(final List<GTFSTrain> trains, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        final EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
        frame.setRecordedAtTime(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        for (final GTFSTrain train : trains) {
            final Optional<ResolvedJourney> resolved =
                    journeyRefResolver.resolve(train.id.trainNumber, train.id.departureDate);
            if (resolved.isEmpty()) {
                continue;
            }
            final EstimatedVehicleJourney evj = buildEvj(train, resolved.get(), now);
            if (evj != null) {
                frame.getEstimatedVehicleJourneies().add(evj);
            }
        }

        delivery.getEstimatedJourneyVersionFrames().add(frame);
        siri.getServiceDelivery().getEstimatedTimetableDeliveries().add(delivery);
        return siri;
    }

    private EstimatedVehicleJourney buildEvj(final GTFSTrain train, final ResolvedJourney journey,
                                             final ZonedDateTime now) {
        final EstimatedVehicleJourney evj = new EstimatedVehicleJourney();

        evj.setRecordedAtTime(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        final LineRef lineRef = new LineRef();
        lineRef.setValue(journey.lineId());
        evj.setLineRef(lineRef);

        final DirectionRefStructure dirRef = new DirectionRefStructure();
        dirRef.setValue("0");
        evj.setDirectionRef(dirRef);

        final FramedVehicleJourneyRefStructure fvjRef = new FramedVehicleJourneyRefStructure();
        final DataFrameRefStructure dfRef = new DataFrameRefStructure();
        dfRef.setValue(journey.dataFrameRef());
        fvjRef.setDataFrameRef(dfRef);
        fvjRef.setDatedVehicleJourneyRef(journey.serviceJourneyId());
        evj.setFramedVehicleJourneyRef(fvjRef);

        evj.setDataSource(dataSource);
        evj.getVehicleModes().add(VehicleModesEnumeration.RAIL);
        if (journey.operatorRef() != null) {
            final OperatorRefStructure operatorRef = new OperatorRefStructure();
            operatorRef.setValue(journey.operatorRef());
            evj.setOperatorRef(operatorRef);
        }

        if (train.cancelled) {
            evj.setCancellation(true);
        }

        // Collect commercial stops and resolve each stop ref up front: a single unresolvable stop means the
        // sequence cannot be complete, so the whole journey is skipped (IsCompleteStopSequence must be true).
        final List<PairedStop> commercialStops = new ArrayList<>();
        for (final PairedStop stop : pairRows(train.timeTableRows)) {
            if (isCommercial(stop)) {
                commercialStops.add(stop);
            }
        }
        final List<String> stopRefs = new ArrayList<>(commercialStops.size());
        boolean anyLive = false;
        for (final PairedStop stop : commercialStops) {
            final Optional<String> stopRef = resolveStopRef(stop);
            if (stopRef.isEmpty()) {
                return null;
            }
            stopRefs.add(stopRef.get());
            anyLive = anyLive || hasLiveData(stop);
        }

        evj.setMonitored(anyLive);

        final List<RecordedCall> recordedCalls = new ArrayList<>();
        final List<EstimatedCall> estimatedCalls = new ArrayList<>();
        for (int i = 0; i < commercialStops.size(); i++) {
            final PairedStop stop = commercialStops.get(i);
            final String stopRefValue = stopRefs.get(i);
            final int order = i + 1;
            // Profile partial-cancellation boundary: the last served stop before a cancelled stop departs 'cancelled'.
            final boolean nextCancelled = i + 1 < commercialStops.size() && isCancelled(commercialStops.get(i + 1));
            if (hasAnyActualTime(stop)) {
                recordedCalls.add(buildRecordedCall(stop, stopRefValue, order, nextCancelled));
            } else {
                estimatedCalls.add(buildEstimatedCall(stop, stopRefValue, order, nextCancelled));
            }
        }

        if (!recordedCalls.isEmpty()) {
            final EstimatedVehicleJourney.RecordedCalls rc = new EstimatedVehicleJourney.RecordedCalls();
            rc.getRecordedCalls().addAll(recordedCalls);
            evj.setRecordedCalls(rc);
        }
        if (!estimatedCalls.isEmpty()) {
            final EstimatedVehicleJourney.EstimatedCalls ec = new EstimatedVehicleJourney.EstimatedCalls();
            ec.getEstimatedCalls().addAll(estimatedCalls);
            evj.setEstimatedCalls(ec);
        }

        evj.setIsCompleteStopSequence(true);
        return evj;
    }

    private RecordedCall buildRecordedCall(final PairedStop stop, final String stopRefValue, final int order,
                                           final boolean nextCancelled) {
        final RecordedCall call = new RecordedCall();
        final StopPointRefStructure ref = new StopPointRefStructure();
        ref.setValue(stopRefValue);
        call.setStopPointRef(ref);
        call.setOrder(BigInteger.valueOf(order));
        // RecordedCallStructure has no RequestStop / PredictionInaccurate in the Nordic profile (EstimatedCall only).

        final boolean cancelled = isCancelled(stop);
        if (cancelled) {
            call.setCancellation(true);
        }

        if (stop.arrival != null) {
            call.setAimedArrivalTime(toHelsinki(stop.arrival.scheduledTime));
            call.setActualArrivalTime(toHelsinki(stop.arrival.actualTime));
            call.setArrivalStatus(cancelled ? CallStatusEnumeration.CANCELLED : timeStatus(stop.arrival));
            call.setArrivalBoardingActivity(cancelled
                    ? ArrivalBoardingActivityEnumeration.NO_ALIGHTING
                    : ArrivalBoardingActivityEnumeration.ALIGHTING);
        }
        if (stop.departure != null) {
            call.setAimedDepartureTime(toHelsinki(stop.departure.scheduledTime));
            call.setActualDepartureTime(toHelsinki(stop.departure.actualTime));
            if (stop.departure.actualTime == null && stop.departure.liveEstimateTime != null) {
                call.setExpectedDepartureTime(toHelsinki(stop.departure.liveEstimateTime));
            }
            final boolean departureCancelled = cancelled || nextCancelled;
            call.setDepartureStatus(departureCancelled ? CallStatusEnumeration.CANCELLED : timeStatus(stop.departure));
            call.setDepartureBoardingActivity(departureCancelled
                    ? DepartureBoardingActivityEnumeration.NO_BOARDING
                    : DepartureBoardingActivityEnumeration.BOARDING);
        }
        return call;
    }

    private EstimatedCall buildEstimatedCall(final PairedStop stop, final String stopRefValue, final int order,
                                             final boolean nextCancelled) {
        final EstimatedCall call = new EstimatedCall();
        final StopPointRefStructure ref = new StopPointRefStructure();
        ref.setValue(stopRefValue);
        call.setStopPointRef(ref);
        call.setOrder(BigInteger.valueOf(order));
        call.setRequestStop(false);

        final boolean cancelled = isCancelled(stop);
        if (cancelled) {
            call.setCancellation(true);
        }
        if (isPredictionInaccurate(stop)) {
            call.setPredictionInaccurate(true);
        }

        if (stop.arrival != null) {
            call.setAimedArrivalTime(toHelsinki(stop.arrival.scheduledTime));
            if (stop.arrival.liveEstimateTime != null) {
                call.setExpectedArrivalTime(toHelsinki(stop.arrival.liveEstimateTime));
            }
            call.setArrivalStatus(cancelled ? CallStatusEnumeration.CANCELLED : timeStatus(stop.arrival));
            call.setArrivalBoardingActivity(cancelled
                    ? ArrivalBoardingActivityEnumeration.NO_ALIGHTING
                    : ArrivalBoardingActivityEnumeration.ALIGHTING);
        }
        if (stop.departure != null) {
            call.setAimedDepartureTime(toHelsinki(stop.departure.scheduledTime));
            if (stop.departure.liveEstimateTime != null) {
                call.setExpectedDepartureTime(toHelsinki(stop.departure.liveEstimateTime));
            }
            final boolean departureCancelled = cancelled || nextCancelled;
            call.setDepartureStatus(departureCancelled ? CallStatusEnumeration.CANCELLED : timeStatus(stop.departure));
            call.setDepartureBoardingActivity(departureCancelled
                    ? DepartureBoardingActivityEnumeration.NO_BOARDING
                    : DepartureBoardingActivityEnumeration.BOARDING);
        }
        return call;
    }

    private Optional<String> resolveStopRef(final PairedStop stop) {
        final GTFSTimeTableRow representative = stop.arrival != null ? stop.arrival : stop.departure;
        final OptionalInt uic = stationUicLookup.uicFor(representative.stationShortCode);
        if (uic.isEmpty()) {
            return Optional.empty();
        }
        final String track = BooleanUtils.isTrue(representative.unknownTrack)
                ? null
                : representative.commercialTrack;
        return siriStopResolver.resolveQuayId(uic.getAsInt(), track);
    }

    private static boolean hasAnyActualTime(final PairedStop stop) {
        if (stop.arrival != null && stop.arrival.actualTime != null) {
            return true;
        }
        return stop.departure != null && stop.departure.actualTime != null;
    }

    private static boolean isCommercial(final PairedStop stop) {
        // Origin (departure only) and terminus (arrival only) are always commercial
        if (stop.arrival == null || stop.departure == null) {
            return true;
        }
        return BooleanUtils.isTrue(stop.arrival.commercialStop)
                || BooleanUtils.isTrue(stop.departure.commercialStop);
    }

    private static boolean isCancelled(final PairedStop stop) {
        if (stop.arrival != null && stop.arrival.cancelled) {
            return true;
        }
        return stop.departure != null && stop.departure.cancelled;
    }

    private static ZonedDateTime toHelsinki(final ZonedDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE);
    }

    /** Per-call status from the row's delay; deviations within a minute are treated as on-time. */
    private static CallStatusEnumeration timeStatus(final GTFSTimeTableRow row) {
        final long delaySeconds = row.delayInSeconds();
        if (delaySeconds >= 60) {
            return CallStatusEnumeration.DELAYED;
        }
        if (delaySeconds <= -60) {
            return CallStatusEnumeration.EARLY;
        }
        return CallStatusEnumeration.ON_TIME;
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
     * Pairs time table rows into stops: origin (DEPARTURE only), middle (ARRIVAL+DEPARTURE), terminus (ARRIVAL only).
     */
    private static List<PairedStop> pairRows(final List<GTFSTimeTableRow> rows) {
        final List<PairedStop> stops = new ArrayList<>();
        if (rows.isEmpty()) {
            return stops;
        }

        int i = 0;
        // First row must be DEPARTURE (origin)
        if (rows.get(0).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
            stops.add(new PairedStop(null, rows.get(0)));
            i = 1;
        }

        // Subsequent rows in ARRIVAL+DEPARTURE pairs
        while (i < rows.size()) {
            final GTFSTimeTableRow arrival = rows.get(i);
            i++;
            if (i < rows.size() && rows.get(i).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
                stops.add(new PairedStop(arrival, rows.get(i)));
                i++;
            } else {
                // Last arrival (terminus) with no following departure
                stops.add(new PairedStop(arrival, null));
            }
        }

        return stops;
    }

    private record PairedStop(GTFSTimeTableRow arrival, GTFSTimeTableRow departure) {}
}
