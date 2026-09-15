package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static fi.livi.rata.avoindata.common.serializer.BigDecimalSerializer.scale;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.vm.model.VmActivity;
import uk.org.siri.siri21.DataFrameRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.LocationStructure;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.NaturalLanguagePlaceNameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleRef;

/// Owns <em>all</em> SIRI-VM XML construction: it assembles the delivery envelope and, for each
/// {@link VmActivity} produced by {@link VmJourneyConverter}, the {@code VehicleActivity}, then serializes the
/// whole {@code Siri} document to bytes. Pure translation — no conversion.
public class VmJourneyMarshaller {

    // How long a reported position stays usable before a client should stop trusting it, measured from when
    // the position was recorded (not from generation time) — same order of magnitude as SIRI-ET's
    // FRESH_WITHIN_MINUTES / the ~10s vehicle-location generation cadence.
    private static final int VALID_UNTIL_MINUTES = 5;
    private final SiriWritingService siriWritingService;
    private final String producerRef;
    private final String dataSource;

    public VmJourneyMarshaller(final SiriWritingService siriWritingService, final String producerRef,
                                final String dataSource) {
        this.siriWritingService = siriWritingService;
        this.producerRef = producerRef;
        this.dataSource = dataSource;
    }

    /// Assembles the complete SIRI-VM {@code ServiceDelivery} document from the converted vehicle activities.
    public Siri marshal(final List<VmActivity> activities, final ZonedDateTime now) {
        final Siri siri = siriWritingService.buildEnvelope(now, producerRef);

        final VehicleMonitoringDeliveryStructure delivery = new VehicleMonitoringDeliveryStructure();
        delivery.setVersion("2.0");
        delivery.setResponseTimestamp(toHelsinki(now));
        for (final VmActivity activity : activities) {
            delivery.getVehicleActivities().add(marshalActivity(activity));
        }

        siri.getServiceDelivery().getVehicleMonitoringDeliveries().add(delivery);
        return siri;
    }

    /**
     * Serializes a built document to UTF-8 XML bytes — the single exit point for SIRI-VM serialization.
     */
    public byte[] marshalToBytes(final Siri siri) {
        return siriWritingService.marshalToBytes(siri);
    }

    private VehicleActivityStructure marshalActivity(final VmActivity activity) {
        final VehicleActivityStructure va = new VehicleActivityStructure();
        va.setRecordedAtTime(toHelsinki(activity.recordedAtTime()));
        va.setValidUntilTime(toHelsinki(activity.recordedAtTime().plusMinutes(VALID_UNTIL_MINUTES)));
        va.setMonitoredVehicleJourney(marshalJourney(activity));
        return va;
    }

    private VehicleActivityStructure.MonitoredVehicleJourney marshalJourney(final VmActivity activity) {
        final VehicleActivityStructure.MonitoredVehicleJourney mvj =
                new VehicleActivityStructure.MonitoredVehicleJourney();

        final LineRef lineRef = new LineRef();
        lineRef.setValue(activity.journey().lineId().value());
        mvj.setLineRef(lineRef);

        final FramedVehicleJourneyRefStructure fvjRef = new FramedVehicleJourneyRefStructure();
        final DataFrameRefStructure dfRef = new DataFrameRefStructure();
        dfRef.setValue(activity.journey().dataFrameRef().value());
        fvjRef.setDataFrameRef(dfRef);
        fvjRef.setDatedVehicleJourneyRef(activity.journey().serviceJourneyId().value());
        mvj.setFramedVehicleJourneyRef(fvjRef);

        mvj.getVehicleModes().add(VehicleModesEnumeration.RAIL);
        if (activity.journey().operatorRef() != null) {
            final OperatorRefStructure operatorRef = new OperatorRefStructure();
            operatorRef.setValue(activity.journey().operatorRef().value());
            mvj.setOperatorRef(operatorRef);
        }
        mvj.setMonitored(true);
        mvj.setDataSource(dataSource);

        if (activity.originStopRef() != null) {
            final JourneyPlaceRefStructure originRef = new JourneyPlaceRefStructure();
            originRef.setValue(activity.originStopRef().value());
            mvj.setOriginRef(originRef);
        }
        if (activity.originName() != null) {
            mvj.getOriginNames().add(placeName(activity.originName()));
        }
        if (activity.destinationStopRef() != null) {
            final DestinationRef destinationRef = new DestinationRef();
            destinationRef.setValue(activity.destinationStopRef().value());
            mvj.setDestinationRef(destinationRef);
        }
        if (activity.destinationName() != null) {
            mvj.getDestinationNames().add(nlString(activity.destinationName()));
        }
        final LocationStructure location = new LocationStructure();
        location.setLongitude(scale(BigDecimal.valueOf(activity.longitude())));
        location.setLatitude(scale(BigDecimal.valueOf(activity.latitude())));
        mvj.setVehicleLocation(location);

        final VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(Long.toString(activity.trainNumber()));
        mvj.setVehicleRef(vehicleRef);

        // Velocity is optional but cheap to report since VmActivity already carries it (converted from the same
        // km/h source GTFS-Realtime uses); round to the nearest whole m/s per the xsd:nonNegativeInteger type.
        // Never negative in practice (reported ground speed), so no clamping needed.
        mvj.setVelocity(BigInteger.valueOf(Math.round(activity.speedMetersPerSecond())));

        if (activity.monitoredCallStopRef() != null) {
            mvj.setMonitoredCall(marshalMonitoredCall(activity));
        }

        // Delay is mandatory in the Nordic SIRI-VM profile (1:1), defined as "PT0S" when there is no delay —
        // it must never be omitted, unlike MonitoredCall/Bearing/Occupancy which are genuinely optional.
        mvj.setDelay(activity.delaySeconds() != null ? Duration.ofSeconds(activity.delaySeconds()) : Duration.ZERO);

        // IsCompleteStopSequence is mandatory (1:1). SIRI-VM only ever reports the single upcoming/current
        // MonitoredCall (never the complete stop sequence like SIRI-ET does), so per profile this must always
        // be reported as false - never omitted or true.
        mvj.setIsCompleteStopSequence(false);

        return mvj;
    }

    private MonitoredCallStructure marshalMonitoredCall(final VmActivity activity) {
        final MonitoredCallStructure call = new MonitoredCallStructure();
        final StopPointRefStructure stopPointRef = new StopPointRefStructure();
        stopPointRef.setValue(activity.monitoredCallStopRef().value());
        call.setStopPointRef(stopPointRef);
        if (activity.monitoredCallStopName() != null) {
            call.getStopPointNames().add(nlString(activity.monitoredCallStopName()));
        }
        if (activity.destinationName() != null) {
            call.getDestinationDisplaies().add(nlString(activity.destinationName()));
        }
        if (activity.vehicleAtStop() != null) {
            call.setVehicleAtStop(activity.vehicleAtStop());
            // VehicleLocationAtStop is "where the vehicle is at the stop" (per the wiki, used for significant
            // deviations from the planned stop location) - only meaningful once VehicleAtStop is true; we have
            // no more precise "at platform" position than the vehicle's own last reported GPS fix.
            if (activity.vehicleAtStop()) {
                final LocationStructure atStop = new LocationStructure();
                atStop.setLongitude(scale(BigDecimal.valueOf(activity.longitude())));
                atStop.setLatitude(scale(BigDecimal.valueOf(activity.latitude())));
                call.setVehicleLocationAtStop(atStop);
            }
        }
        return call;
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

    private static ZonedDateTime toHelsinki(final ZonedDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withZoneSameInstant(DateProvider.ZONE_ID_HKI);
    }
}
