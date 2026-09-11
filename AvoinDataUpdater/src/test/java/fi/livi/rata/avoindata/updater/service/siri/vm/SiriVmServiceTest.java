package fi.livi.rata.avoindata.updater.service.siri.vm;

import static fi.livi.rata.avoindata.common.utils.DateProvider.ZONE_ID_HKI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyEndpoint;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.StationNameLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.StationUicLookup;

class SiriVmServiceTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZONE_ID_HKI);
    private static final ZonedDateTime RECORDED_AT = ZonedDateTime.of(2026, 7, 15, 11, 59, 30, 0, ZONE_ID_HKI);

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:59-12345"), new DataFrameRef("2026-07-15"),
                    new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                    new JourneyPatternRef("FTR:JourneyPattern:59"),
                    new JourneyEndpoint("HKI", "1"), new JourneyEndpoint("OL", "3"));

    private SiriVmService service;
    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        final JourneyRefResolver journeyRefResolver = (trainNumber, date) ->
                trainNumber == 59L ? Optional.of(RESOLVED_59) : Optional.empty();
        final StationUicLookup stationUicLookup = shortCode -> switch (shortCode) {
            case "TPE" -> OptionalInt.of(160);
            case "HKI" -> OptionalInt.of(1);
            case "OL" -> OptionalInt.of(650);
            default -> OptionalInt.empty();
        };
        final StationNameLookup stationNameLookup = shortCode -> switch (shortCode) {
            case "TPE" -> Optional.of("Tampere");
            case "HKI" -> Optional.of("Helsinki");
            case "OL" -> Optional.of("Oulu");
            default -> Optional.empty();
        };
        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000650, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-3", "3", null, null, null))));

        writingService = new SiriWritingService();
        final VmJourneyConverter converter = new VmJourneyConverter(
                journeyRefResolver, stationUicLookup, new SiriStopResolver(petiStopSource.getMatcher()),
                stationNameLookup);
        final VmJourneyMarshaller marshaller = new VmJourneyMarshaller(writingService, "TEST", "FSR");
        service = new SiriVmService(converter, marshaller);
    }

    @Test
    void locationForResolvedTrain_isEmittedAndSchemaValid() {
        final GTFSTrainLocation location = location(59L, "TPE", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(1, result.stats().locationsReceived());
        assertEquals(1, result.stats().activitiesEmitted());
        assertTrue(writingService.isSchemaValid(result.bytes()), "Generated SIRI-VM output must be schema-valid");
    }

    @Test
    void locationForUnresolvedTrain_isSkipped() {
        final GTFSTrainLocation location = location(999L, "TPE", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(1, result.stats().locationsReceived());
        assertEquals(0, result.stats().activitiesEmitted());
    }

    @Test
    void locationWithUnresolvableStop_stillEmittedWithoutMonitoredCall() {
        final GTFSTrainLocation location = location(59L, "UNKNOWN", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(1, result.stats().activitiesEmitted());
        assertTrue(result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getMonitoredCall() == null);
    }

    private static GTFSTrainLocation location(final long trainNumber, final String stationShortCode,
                                               final String commercialTrack) {
        return location(trainNumber, stationShortCode, commercialTrack, null);
    }

    private static GTFSTrainLocation location(final long trainNumber, final String stationShortCode,
                                               final String commercialTrack, final Integer delaySeconds) {
        return location(trainNumber, stationShortCode, commercialTrack, delaySeconds, null);
    }

    private static GTFSTrainLocation location(final long trainNumber, final String stationShortCode,
                                               final String commercialTrack, final Integer delaySeconds,
                                               final Boolean vehicleAtStop) {
        return new TestGTFSTrainLocation(1L, DEPARTURE_DATE, trainNumber, RECORDED_AT,
                24.9384, 60.1699, 90, 10, stationShortCode, commercialTrack, false, delaySeconds, vehicleAtStop);
    }

    @Test
    void locationWithKnownDelay_isEmittedAsDelayDuration() {
        final GTFSTrainLocation location = location(59L, "TPE", "1", 125);

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(java.time.Duration.ofSeconds(125),
                result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                        .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getDelay());
    }

    @Test
    void locationWithUnknownDelay_defaultsToZeroDuration() {
        // Delay is mandatory (1:1) in the Nordic SIRI-VM profile, defined as "PT0S" when there is no delay - it
        // must never be omitted even when the upcoming stop's delay could not be computed.
        final GTFSTrainLocation location = location(59L, "TPE", "1", null);

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(java.time.Duration.ZERO,
                result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                        .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getDelay());
    }

    @Test
    void locationForResolvedTrain_alwaysHasFalseIsCompleteStopSequence() {
        // Mandatory (1:1); SIRI-VM only ever reports the single MonitoredCall, never a complete stop sequence
        // like SIRI-ET, so per profile this must always be false - regardless of whether a MonitoredCall
        // itself was resolvable.
        final GTFSTrainLocation location = location(59L, "UNKNOWN", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(Boolean.FALSE,
                result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                        .getVehicleActivities().getFirst().getMonitoredVehicleJourney().isIsCompleteStopSequence());
    }

    @Test
    void locationForResolvedTrain_emitsVelocityConvertedFromKmhToMs() {
        // 90 km/h -> 25 m/s (see location(...) helper below).
        final GTFSTrainLocation location = location(59L, "TPE", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        assertEquals(java.math.BigInteger.valueOf(25),
                result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                        .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getVelocity());
    }

    @Test
    void locationForResolvedTrain_emitsOriginAndDestinationFromPublishedJourney() {
        final GTFSTrainLocation location = location(59L, "TPE", "1");

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        final var mvj = result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                .getVehicleActivities().getFirst().getMonitoredVehicleJourney();
        assertEquals("FSR:Quay:HKI-1", mvj.getOriginRef().getValue());
        assertEquals("Helsinki", mvj.getOriginNames().getFirst().getValue());
        assertEquals("FSR:Quay:OL-3", mvj.getDestinationRef().getValue());
        assertEquals("Oulu", mvj.getDestinationNames().getFirst().getValue());
        assertEquals("Oulu", mvj.getMonitoredCall().getDestinationDisplaies().getFirst().getValue());
    }

    @Test
    void locationApproachingStop_hasFalseVehicleAtStopAndNoVehicleLocationAtStop() {
        final GTFSTrainLocation location = location(59L, "TPE", "1", null, false);

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        final var call = result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getMonitoredCall();
        assertEquals(Boolean.FALSE, call.isVehicleAtStop());
        assertTrue(call.getVehicleLocationAtStop() == null);
    }

    @Test
    void locationDwellingAtStop_hasTrueVehicleAtStopAndVehicleLocationAtStopFromCurrentPosition() {
        // vehicle_at_stop derives from time_table_row.type (see GTFSTrainRepository#getTrainLocations): once
        // the resolved row is the stop's DEPARTURE (arrival already actual, or origin with no arrival row at
        // all), the train is dwelling there rather than still approaching it.
        final GTFSTrainLocation location = location(59L, "TPE", "1", null, true);

        final SiriVmResult result = service.buildVmDocumentWithStats(List.of(location), NOW);

        final var call = result.document().getServiceDelivery().getVehicleMonitoringDeliveries().getFirst()
                .getVehicleActivities().getFirst().getMonitoredVehicleJourney().getMonitoredCall();
        assertEquals(Boolean.TRUE, call.isVehicleAtStop());
        assertEquals(new java.math.BigDecimal("24.938400"), call.getVehicleLocationAtStop().getLongitude());
        assertEquals(new java.math.BigDecimal("60.169900"), call.getVehicleLocationAtStop().getLatitude());
    }
}
