package fi.livi.rata.avoindata.updater.service.siri.et;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallPoint;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallStatus;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtCall;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;
import fi.livi.rata.avoindata.updater.service.siri.et.model.QuayChange;
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
import uk.org.siri.siri21.NaturalLanguagePlaceNameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.QuayRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopAssignmentStructure;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

/**
 * Owns <em>all</em> SIRI-ET XML construction: it assembles the delivery envelope and, for each
 * {@link EtJourney} produced by {@link EtJourneyInterpreter}, the {@code EstimatedVehicleJourney}, then
 * serializes the whole {@code Siri} document to bytes. Pure translation — no interpretation: it switches on
 * the sealed {@link EtCall} to build a {@code RecordedCall} or {@code EstimatedCall} and does the
 * local-Helsinki time formatting.
 */
public class EtJourneyMarshaller {

    private final SiriWritingService siriWritingService;
    private final String producerRef;
    private final String dataSource;

    public EtJourneyMarshaller(final SiriWritingService siriWritingService, final String producerRef,
                               final String dataSource) {
        this.siriWritingService = siriWritingService;
        this.producerRef = producerRef;
        this.dataSource = dataSource;
    }

    /** Assembles the complete SIRI-ET {@code ServiceDelivery} document from the interpreted journeys. */
    public Siri marshal(final List<EtJourney> journeys, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final EstimatedTimetableDeliveryStructure delivery = new EstimatedTimetableDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        // An EstimatedJourneyVersionFrame must carry at least one journey to be schema-valid; on a no-traffic
        // cycle we emit a bare delivery (no frame) rather than an invalid empty one.
        if (!journeys.isEmpty()) {
            final EstimatedVersionFrameStructure frame = new EstimatedVersionFrameStructure();
            frame.setRecordedAtTime(now.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));
            for (final EtJourney journey : journeys) {
                frame.getEstimatedVehicleJourneies().add(marshalJourney(journey, now));
            }
            delivery.getEstimatedJourneyVersionFrames().add(frame);
        }

        siri.getServiceDelivery().getEstimatedTimetableDeliveries().add(delivery);
        return siri;
    }

    /** Serializes a built document to UTF-8 XML bytes — the single exit point for SIRI-ET serialization. */
    public byte[] marshalToBytes(final Siri siri) {
        return siriWritingService.marshalToBytes(siri);
    }

    private EstimatedVehicleJourney marshalJourney(final EtJourney journey, final ZonedDateTime now) {
        final EstimatedVehicleJourney evj = new EstimatedVehicleJourney();

        evj.setRecordedAtTime(toHelsinki(now));

        final LineRef lineRef = new LineRef();
        lineRef.setValue(journey.lineId().value());
        evj.setLineRef(lineRef);

        final DirectionRefStructure dirRef = new DirectionRefStructure();
        dirRef.setValue("0");
        evj.setDirectionRef(dirRef);

        final FramedVehicleJourneyRefStructure fvjRef = new FramedVehicleJourneyRefStructure();
        final DataFrameRefStructure dfRef = new DataFrameRefStructure();
        dfRef.setValue(journey.dataFrameRef().value());
        fvjRef.setDataFrameRef(dfRef);
        fvjRef.setDatedVehicleJourneyRef(journey.serviceJourneyId().value());
        evj.setFramedVehicleJourneyRef(fvjRef);

        evj.setDataSource(dataSource);
        evj.getVehicleModes().add(VehicleModesEnumeration.RAIL);
        if (journey.operatorRef() != null) {
            final OperatorRefStructure operatorRef = new OperatorRefStructure();
            operatorRef.setValue(journey.operatorRef().value());
            evj.setOperatorRef(operatorRef);
        }
        evj.setMonitored(journey.monitored());
        if (journey.cancelled()) {
            evj.setCancellation(true);
        }
        if (journey.originName() != null) {
            evj.getOriginNames().add(placeName(journey.originName()));
        }
        if (journey.destinationName() != null) {
            evj.getDestinationNames().add(nlString(journey.destinationName()));
        }

        final List<RecordedCall> recordedCalls = new ArrayList<>();
        final List<EstimatedCall> estimatedCalls = new ArrayList<>();
        for (final EtCall call : journey.calls()) {
            switch (call) {
                case EtCall.Recorded recorded -> recordedCalls.add(marshalRecorded(recorded));
                case EtCall.Estimated estimated -> estimatedCalls.add(marshalEstimated(estimated));
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

    private RecordedCall marshalRecorded(final EtCall.Recorded call) {
        final RecordedCall out = new RecordedCall();
        out.setStopPointRef(stopPointRef(call.stopRef().value()));
        out.setOrder(BigInteger.valueOf(call.order()));
        // RecordedCallStructure has no RequestStop / PredictionInaccurate in the Nordic profile (EstimatedCall only).
        if (call.cancelled()) {
            out.setCancellation(true);
        }
        if (call.stopName() != null) {
            out.getStopPointNames().add(nlString(call.stopName()));
        }

        final CallPoint arrival = call.arrival();
        if (arrival != null) {
            out.setAimedArrivalTime(toHelsinki(arrival.aimed()));
            out.setActualArrivalTime(toHelsinki(arrival.actual()));
            out.setArrivalStatus(status(arrival.status()));
            out.setArrivalBoardingActivity(arrivalBoarding(arrival.status()));
        }
        final CallPoint departure = call.departure();
        if (departure != null) {
            out.setAimedDepartureTime(toHelsinki(departure.aimed()));
            out.setActualDepartureTime(toHelsinki(departure.actual()));
            if (departure.actual() == null && departure.expected() != null) {
                out.setExpectedDepartureTime(toHelsinki(departure.expected()));
            }
            out.setDepartureStatus(status(departure.status()));
            out.setDepartureBoardingActivity(departureBoarding(departure.status()));
        }
        addStopAssignment(call, out.getArrivalStopAssignments(), out.getDepartureStopAssignments());
        return out;
    }

    private EstimatedCall marshalEstimated(final EtCall.Estimated call) {
        final EstimatedCall out = new EstimatedCall();
        out.setStopPointRef(stopPointRef(call.stopRef().value()));
        out.setOrder(BigInteger.valueOf(call.order()));
        out.setRequestStop(false);
        if (call.cancelled()) {
            out.setCancellation(true);
        }
        if (call.predictionInaccurate()) {
            out.setPredictionInaccurate(true);
        }
        if (call.stopName() != null) {
            out.getStopPointNames().add(nlString(call.stopName()));
        }

        final CallPoint arrival = call.arrival();
        if (arrival != null) {
            out.setAimedArrivalTime(toHelsinki(arrival.aimed()));
            if (arrival.expected() != null) {
                out.setExpectedArrivalTime(toHelsinki(arrival.expected()));
            }
            out.setArrivalStatus(status(arrival.status()));
            out.setArrivalBoardingActivity(arrivalBoarding(arrival.status()));
        }
        final CallPoint departure = call.departure();
        if (departure != null) {
            out.setAimedDepartureTime(toHelsinki(departure.aimed()));
            if (departure.expected() != null) {
                out.setExpectedDepartureTime(toHelsinki(departure.expected()));
            }
            out.setDepartureStatus(status(departure.status()));
            out.setDepartureBoardingActivity(departureBoarding(departure.status()));
        }
        addStopAssignment(call, out.getArrivalStopAssignments(), out.getDepartureStopAssignments());
        return out;
    }

    /**
     * Adds a {@code StopAssignment} (aimed + expected quay) for a genuine platform change. It sits on the
     * arrival side when the call has an arrival (per the profile example); the origin (arrival-only-absent)
     * carries it on departure.
     */
    private static void addStopAssignment(final EtCall call, final List<StopAssignmentStructure> arrivalAssignments,
                                          final List<StopAssignmentStructure> departureAssignments) {
        final QuayChange quayChange = call.quayChange();
        if (quayChange == null) {
            return;
        }
        final StopAssignmentStructure assignment = new StopAssignmentStructure();
        assignment.setAimedQuayRef(quayRef(quayChange.aimed().value()));
        assignment.setExpectedQuayRef(quayRef(quayChange.expected().value()));
        if (call.arrival() != null) {
            arrivalAssignments.add(assignment);
        } else {
            departureAssignments.add(assignment);
        }
    }

    private static QuayRefStructure quayRef(final String value) {
        final QuayRefStructure ref = new QuayRefStructure();
        ref.setValue(value);
        return ref;
    }

    private static StopPointRefStructure stopPointRef(final String value) {
        final StopPointRefStructure ref = new StopPointRefStructure();
        ref.setValue(value);
        return ref;
    }

    private static NaturalLanguageStringStructure nlString(final String value) {
        final NaturalLanguageStringStructure name = new NaturalLanguageStringStructure();
        name.setValue(value);
        return name;
    }

    private static NaturalLanguagePlaceNameStructure placeName(final String value) {
        final NaturalLanguagePlaceNameStructure name = new NaturalLanguagePlaceNameStructure();
        name.setValue(value);
        return name;
    }

    private static CallStatusEnumeration status(final CallStatus status) {
        return switch (status) {
            case ON_TIME -> CallStatusEnumeration.ON_TIME;
            case DELAYED -> CallStatusEnumeration.DELAYED;
            case EARLY -> CallStatusEnumeration.EARLY;
            case CANCELLED -> CallStatusEnumeration.CANCELLED;
        };
    }

    private static ArrivalBoardingActivityEnumeration arrivalBoarding(final CallStatus status) {
        return status == CallStatus.CANCELLED
                ? ArrivalBoardingActivityEnumeration.NO_ALIGHTING
                : ArrivalBoardingActivityEnumeration.ALIGHTING;
    }

    private static DepartureBoardingActivityEnumeration departureBoarding(final CallStatus status) {
        return status == CallStatus.CANCELLED
                ? DepartureBoardingActivityEnumeration.NO_BOARDING
                : DepartureBoardingActivityEnumeration.BOARDING;
    }

    private static ZonedDateTime toHelsinki(final ZonedDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE);
    }
}
