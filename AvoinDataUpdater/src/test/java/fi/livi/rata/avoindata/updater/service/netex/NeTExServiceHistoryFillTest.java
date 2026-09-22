package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.gtfs.TrackObservation;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.gtfs.TimeTableRowService;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialTrackResolver;
import fi.livi.rata.avoindata.updater.service.timetable.HistoricalTrackSource;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * Filling a track from what the train was last seen on, matched on the exact
 * schedule
 * part (attapId).
 */
class NeTExServiceHistoryFillTest {

    private static final String PETI_URL = "https://rae.fintraffic.fi/exports/PETI-rail-NeTEx.zip";
    private static final List<String> STATIONS = List.of("HKI", "PSL", "TPE");

    /**
     * A departure observation of the exact schedule part fills the stop: a row that
     * both
     * arrives and departs carries one track, so either part answers it.
     */
    @Test
    void givenExactSchedulePartObserved_whenFillingIntermediateStop_thenTrackIsTaken() throws Exception {
        final Schedule schedule = createSchedule();
        // PSL (index 1) departs on schedule part (attapId) 5
        final TrackObservation departureOnly = new TrackObservation(59L, 5L, "PSL",
                TimeTableRow.TimeTableRowType.DEPARTURE, "5", ZonedDateTime.now());

        createService(schedule, List.of(departureOnly)).generateNeTEx();

        assertEquals("5", schedule.scheduleRows.get(1).commercialTrack);
    }

    @Test
    void givenNoObservations_whenFilling_thenStopKeepsNoTrack() throws Exception {
        final Schedule schedule = createSchedule();

        createService(schedule, List.of()).generateNeTEx();

        assertEquals(null, schedule.scheduleRows.get(1).commercialTrack);
    }

    private NeTExService createService(final Schedule schedule, final List<TrackObservation> observations)
            throws Exception {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExRouteService routeService = new NeTExRouteService(idGenerator);
        final var petiSource = petiSourceWithOneStopPlace();

        final ScheduleProviderService scheduleProviderService = mock(ScheduleProviderService.class);
        when(scheduleProviderService.getAdhocSchedules(any())).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any())).thenReturn(List.of(schedule));

        final StationRepository stationRepository = mock(StationRepository.class);
        when(stationRepository.findAll()).thenReturn(createStations());

        final TimeTableRowService timeTableRowService = mock(TimeTableRowService.class);
        when(timeTableRowService.getNextTenDays()).thenReturn(List.of());

        final TimeTableRowRepository timeTableRowRepository = mock(TimeTableRowRepository.class);
        when(timeTableRowRepository.findObservedTracks(any(), any(), anyCollection())).thenReturn(observations);

        final NeTExService service = new NeTExService(
                new NeTExEntityService(idGenerator, new NeTExTimeConverter()),
                new NeTExCalendarService(idGenerator), routeService, new IdentityTrackSource(),
                new NeTExStopsService(idGenerator, petiSource), new NeTExWritingService(idGenerator, PETI_URL),
                petiSource, scheduleProviderService, new TodaysScheduleService(), stationRepository,
                new CommercialTrackResolver(), timeTableRowService,
                new HistoricalTrackSource(timeTableRowRepository));

        final Field field = NeTExService.class.getDeclaredField("minMatchRate");
        field.setAccessible(true);
        field.setDouble(service, 0.0);
        return service;
    }

    /**
     * Generation refuses an empty PETI snapshot; these tests are about track
     * filling, not stop matching.
     */
    private static PetiStopSource petiSourceWithOneStopPlace() {
        return () -> List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_100, "HKI station", true, null, List.of()));
    }

    private static List<Station> createStations() {
        final List<Station> stations = new ArrayList<>();
        for (int i = 0; i < STATIONS.size(); i++) {
            final Station station = new Station();
            station.shortCode = STATIONS.get(i);
            station.name = STATIONS.get(i) + " station";
            station.uicCode = 100 + i;
            station.passengerTraffic = true;
            station.latitude = new BigDecimal("60.17").add(new BigDecimal(i));
            station.longitude = new BigDecimal("24.94").add(new BigDecimal(i));
            station.countryCode = "FI";
            stations.add(station);
        }
        return stations;
    }

    /**
     * Long-distance IC schedule; PSL (index 1) has arrival part 4 and departure
     * part 5.
     */
    private static Schedule createSchedule() {
        final Schedule schedule = new Schedule();
        schedule.id = 1L;
        schedule.trainNumber = 59L;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);
        schedule.effectiveFrom = LocalDate.of(2026, 6, 15);
        schedule.changeType = "L";
        schedule.capacityId = "cap-59";
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
        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = "Long-distance";
        trainType.trainCategory = trainCategory;
        schedule.trainType = trainType;
        schedule.trainCategory = trainCategory;
        schedule.commuterLineId = null;

        schedule.scheduleRows = new ArrayList<>();
        long rowId = 1;
        for (int i = 0; i < STATIONS.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = rowId++;
            row.station = new StationEmbeddable(STATIONS.get(i), 100 + i, "FI");
            if (i > 0) {
                row.arrival = part(rowId++, row, Duration.ofHours(5).plusMinutes(30 + i * 60L));
            }
            if (i < STATIONS.size() - 1) {
                row.departure = part(rowId++, row, Duration.ofHours(5).plusMinutes(31 + i * 60L));
            }
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static ScheduleRowPart part(final long id, final ScheduleRow row, final Duration timestamp) {
        final ScheduleRowPart p = new ScheduleRowPart();
        p.id = id;
        p.timestamp = timestamp;
        p.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        p.scheduleRow = row;
        return p;
    }
}
