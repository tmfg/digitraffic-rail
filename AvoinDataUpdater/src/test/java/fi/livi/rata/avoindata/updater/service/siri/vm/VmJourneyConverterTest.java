package fi.livi.rata.avoindata.updater.service.siri.vm;

import static fi.livi.rata.avoindata.common.utils.DateProvider.ZONE_ID_HKI;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.TimeTableRowsLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.StationNameLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.StationUicLookup;
import fi.livi.rata.avoindata.updater.service.siri.vm.model.VmActivity;

/**
 * Converter-level coverage for {@link VmJourneyConverter#resolveMonitoredCallStopRef}'s unknown-track fallback:
 * unlike {@link SiriVmServiceTest}/{@link SiriVmGoldenXmlTest} (which install no-op planned-track/row lookups
 * and never exercise this path), these tests feed real rows through {@link TimeTableRowsLookup} and a populated
 * {@link fi.livi.rata.avoindata.updater.service.siri.et.PlannedTrackLookup}, so the central fallback (planned
 * track + visit-index resolution via {@code CommercialStopVisits}) is verified directly at the unit that owns
 * it, rather than only through unrelated utility-only tests.
 */
class VmJourneyConverterTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime RECORDED_AT = ZonedDateTime.of(2026, 7, 15, 11, 59, 30, 0, ZONE_ID_HKI);

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:59-12345"), new DataFrameRef("2026-07-15"),
                    new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"), null);

    private static final JourneyRefResolver JOURNEY_REF_RESOLVER =
            (trainNumber, date) -> trainNumber == 59L ? Optional.of(RESOLVED_59) : Optional.empty();

    private static final StationUicLookup STATION_UIC_LOOKUP = shortCode -> switch (shortCode) {
        case "HKI" -> OptionalInt.of(1);
        case "TPE" -> OptionalInt.of(160);
        case "OL" -> OptionalInt.of(650);
        default -> OptionalInt.empty();
    };

    private static final StationNameLookup STATION_NAME_LOOKUP = shortCode -> Optional.of(shortCode);

    private static final SiriStopResolver SIRI_STOP_RESOLVER = new SiriStopResolver(
            ((PetiStopSource) () -> List.of(
                    new PetiStop("FSR:StopPlace:HKI", 1, "Helsinki", true, null,
                            List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null))),
                    new PetiStop("FSR:StopPlace:TPE", 160, "Tampere", true, null,
                            List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null),
                                    new PetiQuay("FSR:Quay:TPE-2", "2", null, null, null),
                                    new PetiQuay("FSR:Quay:TPE-5", "5", null, null, null))),
                    new PetiStop("FSR:StopPlace:OL", 650, "Oulu", true, null,
                            List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null),
                                    new PetiQuay("FSR:Quay:OL-3", "3", null, null, null)))
            )).getMatcher());

    private static GTFSTimeTableRow row(final String stationShortCode, final TimeTableRow.TimeTableRowType type,
                                        final ZonedDateTime scheduledTime, final ZonedDateTime actualTime) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = stationShortCode;
        row.type = type;
        row.scheduledTime = scheduledTime;
        row.actualTime = actualTime;
        row.commercialStop = true;
        row.cancelled = false;
        return row;
    }

    private static GTFSTrainLocation location(final String stationShortCode, final String commercialTrack) {
        return new TestGTFSTrainLocation(1L, DEPARTURE_DATE, 59L, RECORDED_AT,
                24.9384, 60.1699, 90, 10, stationShortCode, commercialTrack, true, null, false, null);
    }

    private static VmJourneyConverter converter(final List<GTFSTimeTableRow> rows,
                                                 final Map<String, String> plannedTracksByVisit) {
        return new VmJourneyConverter(JOURNEY_REF_RESOLVER, STATION_UIC_LOOKUP, SIRI_STOP_RESOLVER,
                STATION_NAME_LOOKUP,
                (trainNumber, departureDate, stationShortCode, visitIndex) ->
                        Optional.ofNullable(plannedTracksByVisit.get(stationShortCode + "#" + visitIndex)),
                (trainNumber, departureDate) -> rows);
    }

    @Test
    void givenUnknownTrackAtNormalStop_whenConvert_thenFallsBackToPlannedTrackQuay() {
        // TPE is served once, not yet reached (no actual time on either row) -> visitIndex 0.
        final List<GTFSTimeTableRow> rows = List.of(
                row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, ZONE_ID_HKI), null),
                row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                        ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZONE_ID_HKI), null),
                row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 12, 5, 0, 0, ZONE_ID_HKI), null));
        final VmJourneyConverter converter =
                converter(rows, Map.of("TPE#0", "5"));

        final VmActivity activity = converter.convert(location("TPE", "9")).orElseThrow();

        assertEquals("FSR:Quay:TPE-5", activity.monitoredCallStopRef().value());
    }

    @Test
    void givenUnknownTrackAtRepeatedStation_whenConvert_thenFallsBackToItsOwnVisitsPlannedTrack() {
        // TPE is served twice: the first visit is already completed (departure actual time set), so the
        // *current* (not-yet-completed) TPE visit is index 1 - the fallback must resolve TPE#1's own planned
        // track ("2"), not TPE#0's ("1").
        final List<GTFSTimeTableRow> rows = List.of(
                row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZONE_ID_HKI)),
                row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                        ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, ZONE_ID_HKI)),
                row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 9, 5, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 9, 5, 0, 0, ZONE_ID_HKI)),
                row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                        ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, ZONE_ID_HKI), null),
                row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, ZONE_ID_HKI), null));
        final VmJourneyConverter converter =
                converter(rows, Map.of("TPE#0", "1", "TPE#1", "2"));

        final VmActivity activity = converter.convert(location("TPE", "9")).orElseThrow();

        assertEquals("FSR:Quay:TPE-2", activity.monitoredCallStopRef().value());
    }

    @Test
    void givenUnknownTrackAtArrivedTerminus_whenConvert_thenFallsBackToTerminusPlannedTrack() {
        // OL is the terminus (arrival-only) and has already arrived (actual time set) - CommercialStopVisits'
        // terminus fallback must still report it as the current visit (index 0) instead of finding nothing.
        final List<GTFSTimeTableRow> rows = List.of(
                row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZONE_ID_HKI)),
                row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                        ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, ZONE_ID_HKI)),
                row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                        ZonedDateTime.of(2026, 7, 15, 9, 5, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 9, 5, 0, 0, ZONE_ID_HKI)),
                row("OL", TimeTableRow.TimeTableRowType.ARRIVAL,
                        ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, ZONE_ID_HKI),
                        ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, ZONE_ID_HKI)));
        final VmJourneyConverter converter =
                converter(rows, Map.of("OL#0", "3"));

        final VmActivity activity = converter.convert(location("OL", "9")).orElseThrow();

        assertEquals("FSR:Quay:OL-3", activity.monitoredCallStopRef().value());
    }
}
