package fi.livi.rata.avoindata.updater.service.siri.et;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallPoint;
import fi.livi.rata.avoindata.updater.service.siri.et.model.CallStatus;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtCall;
import fi.livi.rata.avoindata.updater.service.siri.et.model.EtJourney;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DataFrameRefStructure;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.DirectionRefStructure;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

/**
 * Marshals an {@link EtJourney} into a SIRI {@code EstimatedVehicleJourney}. Pure translation — no
 * interpretation: it switches on the sealed {@link EtCall} to build a {@code RecordedCall} or
 * {@code EstimatedCall} and does the local-Helsinki time formatting.
 */
public class EtJourneyMarshaller {

    private final String dataSource;

    public EtJourneyMarshaller(final String dataSource) {
        this.dataSource = dataSource;
    }

    public EstimatedVehicleJourney marshal(final EtJourney journey, final ZonedDateTime now) {
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
        return out;
    }

    private static StopPointRefStructure stopPointRef(final String value) {
        final StopPointRefStructure ref = new StopPointRefStructure();
        ref.setValue(value);
        return ref;
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
