package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialTrackResolver;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * A track PETI does not publish as a platform (a yard or work track) must not
 * reach the feed: it is
 * replaced with the station's first platform and reported. Also covers the
 * postcondition that every
 * stop point leaves with an assignment.
 */
class NeTExServiceUnknownTrackTest {

    private static final String PETI_URL = "https://rae.fintraffic.fi/exports/PETI-rail-NeTEx.zip";

    @Test
    void givenTrackPetiDoesNotPublish_whenGenerating_thenStopUsesFirstPlatform() {
        // given — HKI publishes platforms 1 and 2, the schedule says the train stops on
        // yard track 415
        final NeTExService service = serviceWith(helsinkiWithPlatforms("1", "2"));
        final Schedule schedule = scheduleWithTracks("415", "1");

        // when
        final var result = service.generateNeTEx(List.of(), List.of(schedule), stations());

        // then — the stop point is track-qualified to the first platform, never to 415
        final List<String> sspIds = result.dataset().stopsData().getScheduledStopPoints().stream()
                .map(NeTExStopsData.NeTExScheduledStopPoint::id)
                .toList();
        assertTrue(sspIds.contains("FTR:ScheduledStopPoint:HKI-1"), sspIds.toString());
        assertFalse(sspIds.contains("FTR:ScheduledStopPoint:HKI-415"), sspIds.toString());
    }

    @Test
    void givenTrackPetiDoesNotPublish_whenGenerating_thenErrorNamesTheOriginalTrack() {
        final NeTExService service = serviceWith(helsinkiWithPlatforms("1", "2"));
        final Schedule schedule = scheduleWithTracks("415", "1");

        final Logger logbackLogger = (Logger) LoggerFactory.getLogger(NeTExService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            service.generateNeTEx(List.of(), List.of(schedule), stations());

            final String message = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("PETI publishes no platform"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an error, got: " + appender.list));

            assertTrue(message.contains("station=HKI"), message);
            assertTrue(message.contains("track=415"), message);
            assertTrue(message.contains("petiTracks=[1, 2]"), message);
            assertTrue(message.contains("replacedWith=1"), message);
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    @Test
    void givenTrackOutsidePlatformNumbering_whenGenerating_thenBlamedOnTheSchedule() {
        // 415 is not a platform number at all, so the schedule put a passenger train on
        // a yard track
        assertCause("415", "invalid_schedule_track", "report to the schedule source");
    }

    @Test
    void givenPlatformShapedTrackPetiLacks_whenGenerating_thenBlamedOnPeti() {
        // 3 looks like an ordinary platform, so PETI is the one missing data
        assertCause("3", "missing_from_peti", "report to PETI");
    }

    private void assertCause(final String track, final String expectedCause, final String expectedSummary) {
        final NeTExService service = serviceWith(helsinkiWithPlatforms("1", "2"));
        final Schedule schedule = scheduleWithTracks(track, "1");

        final Logger logbackLogger = (Logger) LoggerFactory.getLogger(NeTExService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            service.generateNeTEx(List.of(), List.of(schedule), stations());

            final List<String> errors = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertTrue(errors.stream().anyMatch(m -> m.contains("track=" + track)
                    && m.contains("likelyCause=" + expectedCause)), errors.toString());
            assertTrue(errors.stream().anyMatch(m -> m.contains(expectedSummary)
                    && m.contains("HKI-" + track)), errors.toString());
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    @Test
    void givenTrackPetiPublishes_whenGenerating_thenTrackIsKept() {
        final NeTExService service = serviceWith(helsinkiWithPlatforms("1", "2"));
        final Schedule schedule = scheduleWithTracks("2", "1");

        final var result = service.generateNeTEx(List.of(), List.of(schedule), stations());

        final List<String> sspIds = result.dataset().stopsData().getScheduledStopPoints().stream()
                .map(NeTExStopsData.NeTExScheduledStopPoint::id)
                .toList();
        assertTrue(sspIds.contains("FTR:ScheduledStopPoint:HKI-2"), sspIds.toString());
    }

    @Test
    void givenStationMissingFromPeti_whenGenerating_thenPostconditionNamesTheStopPointWithoutAssignment() {
        // given — PETI publishes Helsinki but not Tampere, so nothing can assign
        // Tampere's stop point
        final NeTExService service = serviceWith(onlyHelsinki());
        final Schedule schedule = scheduleWithTracks("1", "1");

        final Logger logbackLogger = (Logger) LoggerFactory.getLogger(NeTExService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            service.generateNeTEx(List.of(), List.of(schedule), stations());

            final String message = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("method=checkStopAssignments"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an error, got: " + appender.list));

            assertTrue(message.contains("withoutAssignment=1"), message);
            assertTrue(message.contains("FTR:ScheduledStopPoint:TPE-1"), message);
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    @Test
    void givenEveryStopPointAssigned_whenGenerating_thenPostconditionLogsNothing() {
        final NeTExService service = serviceWith(helsinkiWithPlatforms("1", "2"));
        final Schedule schedule = scheduleWithTracks("2", "1");

        final Logger logbackLogger = (Logger) LoggerFactory.getLogger(NeTExService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            service.generateNeTEx(List.of(), List.of(schedule), stations());

            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(m -> m.contains("method=checkStopAssignments")),
                    "Expected no report when every stop point is assigned: " + appender.list);
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    /** TPE is absent, so its stop point cannot be assigned. */
    private static PetiStopSource onlyHelsinki() {
        final PetiStop hki = new PetiStop("FSR:StopPlace:HKI", 1_000_100, "Helsinki", true, null,
                List.of(new PetiQuay("FSR:Quay:HKI-1", "1", null, null, null)));
        return () -> List.of(hki);
    }

    /** HKI is station uic 100, so its PETI stop place is 1_000_100. */
    private static PetiStopSource helsinkiWithPlatforms(final String... publicCodes) {
        final List<PetiQuay> quays = new ArrayList<>();
        for (final String code : publicCodes) {
            quays.add(new PetiQuay("FSR:Quay:HKI-" + code, code, null, null, null));
        }
        final PetiStop hki = new PetiStop("FSR:StopPlace:HKI", 1_000_100, "Helsinki", true, null, quays);
        final PetiStop tpe = new PetiStop("FSR:StopPlace:TPE", 1_000_101, "Tampere", true, null,
                List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null)));
        return () -> List.of(hki, tpe);
    }

    private static List<Station> stations() {
        final List<Station> stations = new ArrayList<>();
        int uic = 100;
        for (final String code : List.of("HKI", "TPE")) {
            final Station station = new Station();
            station.shortCode = code;
            station.name = code + " station";
            station.uicCode = uic++;
            station.passengerTraffic = true;
            station.latitude = new BigDecimal("60.17");
            station.longitude = new BigDecimal("24.94");
            station.countryCode = "FI";
            stations.add(station);
        }
        return stations;
    }

    private static Schedule scheduleWithTracks(final String... tracks) {
        final Schedule schedule = new Schedule();
        schedule.id = 1L;
        schedule.trainNumber = 66L;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);
        schedule.effectiveFrom = LocalDate.of(2026, 6, 15);
        schedule.changeType = "L";
        schedule.capacityId = "cap-66";
        schedule.typeCode = "L";
        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnWednesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = false;
        schedule.runOnSunday = false;
        schedule.scheduleCancellations = new HashSet<>();
        schedule.scheduleExceptions = new HashSet<>();
        schedule.operator = new Operator(10, "vr");

        final TrainType trainType = new TrainType();
        trainType.name = "IC";
        trainType.commercial = true;
        final TrainCategory category = new TrainCategory();
        category.name = "Long-distance";
        trainType.trainCategory = category;
        schedule.trainType = trainType;
        schedule.trainCategory = category;

        final List<String> codes = List.of("HKI", "TPE");
        schedule.scheduleRows = new ArrayList<>();
        long rowId = 1;
        for (int i = 0; i < codes.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = rowId++;
            row.station = new StationEmbeddable(codes.get(i), 100 + i, "FI");
            row.commercialTrack = tracks[i];

            if (i > 0) {
                final ScheduleRowPart arrival = new ScheduleRowPart();
                arrival.id = rowId++;
                arrival.timestamp = Duration.ofHours(5).plusMinutes(30L + i * 60);
                arrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                arrival.scheduleRow = row;
                row.arrival = arrival;
            }
            if (i < codes.size() - 1) {
                final ScheduleRowPart departure = new ScheduleRowPart();
                departure.id = rowId++;
                departure.timestamp = Duration.ofHours(5).plusMinutes(31L + i * 60);
                departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                departure.scheduleRow = row;
                row.departure = departure;
            }
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static NeTExService serviceWith(final PetiStopSource petiSource) {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExService service = new NeTExService(
                new NeTExEntityService(idGenerator, new NeTExTimeConverter()),
                new NeTExCalendarService(idGenerator),
                new NeTExRouteService(idGenerator),
                new IdentityTrackSource(),
                new NeTExStopsService(idGenerator, petiSource),
                new NeTExWritingService(idGenerator, PETI_URL),
                petiSource, null, new TodaysScheduleService(), null,
                new CommercialTrackResolver(), null, null);
        try {
            final Field field = NeTExService.class.getDeclaredField("minMatchRate");
            field.setAccessible(true);
            field.setDouble(service, 0.0);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to set minMatchRate", e);
        }
        return service;
    }
}
