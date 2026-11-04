package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialTrackResolver;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * The journey refs stored for SIRI cover only the stored window (2 days back
 * to 14 days ahead by default), while the NeTEx calendar and XML keep every
 * operating day of the package.
 */
class NeTExServicePublishedWindowTest {

    private static final String PETI_URL = "https://rae.fintraffic.fi/exports/PETI-rail-NeTEx.zip";

    @Test
    void givenTrainRunningEveryDay_whenGenerating_thenDraftsCoverOnlyTheStoredWindow() {
        final LocalDate today = DateProvider.dateInHelsinki();
        final Schedule schedule = scheduleRunningEveryDay(today.minusDays(30), today.plusDays(60));

        final var result = service().generateNeTEx(List.of(), List.of(schedule), stations());

        final TreeSet<LocalDate> draftDates = new TreeSet<>();
        result.dataset().publishedJourneys().forEach(d -> draftDates.add(d.trainId().departureDate));
        // Both ends are inclusive and the days next to them are left out.
        assertEquals(today.minusDays(2), draftDates.first());
        assertEquals(today.plusDays(14), draftDates.last());
        assertEquals(17, result.dataset().publishedJourneys().size());
        assertEquals(17, draftDates.size());
    }

    @Test
    void givenTrainRunningEveryDay_whenGenerating_thenDatesOutsideTheWindowStayInTheNeTExCalendar() {
        final LocalDate today = DateProvider.dateInHelsinki();
        final Schedule schedule = scheduleRunningEveryDay(today.minusDays(30), today.plusDays(60));

        final var result = service().generateNeTEx(List.of(), List.of(schedule), stations());

        final List<LocalDate> operatingDays = result.operatingDays();
        assertTrue(operatingDays.contains(today.minusDays(3)), "day before the window");
        assertTrue(operatingDays.contains(today.plusDays(15)), "day after the window");
        assertTrue(operatingDays.contains(today.plusDays(60)), "last day of the schedule");
        assertEquals(operatingDays, result.dataset().operatingDays());
    }

    @Test
    void givenPersistingDisabled_whenGenerating_thenNoDraftsAreBuilt() {
        final LocalDate today = DateProvider.dateInHelsinki();
        final Schedule schedule = scheduleRunningEveryDay(today.minusDays(30), today.plusDays(60));
        final NeTExService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "persistJourneysEnabled", false);

        final var result = service.generateNeTEx(List.of(), List.of(schedule), stations());

        assertTrue(result.dataset().publishedJourneys().isEmpty());
        assertTrue(result.operatingDays().contains(today.plusDays(15)));
    }

    private static NeTExService service() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final PetiStopSource petiSource = () -> List.of(
                new PetiStop("FSR:StopPlace:HKI", 1_000_100, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1_000_101, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))));
        return new NeTExService(
                new NeTExEntityService(idGenerator, new NeTExTimeConverter()),
                new NeTExCalendarService(idGenerator),
                new NeTExRouteService(idGenerator),
                new IdentityTrackSource(),
                new NeTExStopsService(idGenerator, petiSource),
                new NeTExWritingService(idGenerator, PETI_URL),
                petiSource, null, new TodaysScheduleService(), null,
                new CommercialTrackResolver(), null, null);
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

    private static Schedule scheduleRunningEveryDay(final LocalDate start, final LocalDate end) {
        final Schedule schedule = new Schedule();
        schedule.id = 1L;
        schedule.trainNumber = 66L;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = start;
        schedule.endDate = end;
        schedule.effectiveFrom = start;
        schedule.changeType = "L";
        schedule.capacityId = "cap-66";
        schedule.typeCode = "L";
        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnWednesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;
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
            row.commercialTrack = "1";
            if (i > 0) {
                row.arrival = part(rowId++, row, Duration.ofHours(5).plusMinutes(30L + i * 60));
            }
            if (i < codes.size() - 1) {
                row.departure = part(rowId++, row, Duration.ofHours(5).plusMinutes(31L + i * 60));
            }
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static ScheduleRowPart part(final long id, final ScheduleRow row, final Duration timestamp) {
        final ScheduleRowPart part = new ScheduleRowPart();
        part.id = id;
        part.timestamp = timestamp;
        part.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        part.scheduleRow = row;
        return part;
    }
}
