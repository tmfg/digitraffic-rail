package fi.livi.rata.avoindata.updater.service.gtfs;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

@Service
public class GTFSService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private GTFSTripRepository gtfsTripRepository;

    @Autowired
    private GTFSEntityService gtfsEntityService;

    @Autowired
    private GTFSWritingService gtfsWritingService;

    @Autowired
    private DateProvider dp;

    @Autowired
    private ScheduleProviderService scheduleProviderService;

    @Autowired
    private LastUpdateService lastUpdateService;

    @Scheduled(cron = "${updater.gtfs.cron}", zone = "Europe/Helsinki")
    public void generateGTFS() {
        try {
            final LocalDate start = dp.dateInHelsinki().minusDays(7);
            this.generateGTFS(scheduleProviderService.getAdhocSchedules(start), scheduleProviderService.getRegularSchedules(start));

            lastUpdateService.update(LastUpdateService.LastUpdatedType.GTFS);
        } catch (ExecutionException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

//For generating test json
//    @PostConstruct
//    public void writeJson() throws ExecutionException, InterruptedException, IOException {
//        List<Schedule> allSchedules = new ArrayList<>();
//        allSchedules.addAll(scheduleProviderService.getAdhocSchedules(LocalDate.now()));
//        allSchedules.addAll(scheduleProviderService.getRegularSchedules(LocalDate.now()));
//
//        Set<Long> trainNumbers = Sets.newHashSet(141L,151L);
//        List<Schedule> filteredSchedules = allSchedules.stream().filter(schedule -> trainNumbers.contains( schedule.trainNumber)).collect(Collectors.toList());
//
//        log.info("Ids {}",filteredSchedules.stream().map(s->s.id).collect(Collectors.toList()));
//    }

    private GTFSDto createGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules, String zipFileName) throws IOException {
        GTFSDto gfsDto = gtfsEntityService.createGTFSEntity(passengerAdhocSchedules, passengerRegularSchedules);

        for (Stop stop : gfsDto.stops) {
            stop.name = stop.name.replace(" asema", "");
        }

        for (Trip trip : gfsDto.trips) {
            trip.headsign = trip.headsign.replace(" asema", "");
        }

        for (Trip trip : gfsDto.trips) {
            if (trip.stopTimes != null) {
                if (trip.stopTimes.get(0).stopId.equals("HKI") && Iterables.getLast(trip.stopTimes).stopId.equals("HKI") && trip.stopTimes.stream().map(s -> s.stopId).anyMatch(s -> s.equals("LEN"))) {
                    trip.headsign = "Helsinki -> Lentoasema -> Helsinki";
                }
            } else {
                log.error("Encountered trip without stoptimes: {}", trip);
            }
        }

        gtfsWritingService.writeGTFSFiles(gfsDto, zipFileName);

        return gfsDto;
    }

    public void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) throws IOException {
        final GTFSDto gtfs = createGtfs(adhocSchedules, regularSchedules, "gtfs-all.zip");

        final List<Schedule> passengerAdhocSchedules = Lists.newArrayList(
                Collections2.filter(adhocSchedules, s -> isPassengerTrain(s)));
        final List<Schedule> passengerRegularSchedules = Lists.newArrayList(
                Collections2.filter(regularSchedules, s -> isPassengerTrain(s)));

        createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger.zip");
        createVrGtfs(passengerAdhocSchedules, passengerRegularSchedules);

        updateGtfsTrips(gtfs);

        log.info("Successfully wrote GTFS files");
    }

    private void updateGtfsTrips(final GTFSDto gtfs) {
        final List<GTFSTrip> gtfsTrips = gtfs.trips.stream()
                .map(trip -> new GTFSTrip(trip.source.trainNumber, trip.calendar.startDate, trip.calendar.endDate, trip.tripId, trip.routeId))
                .collect(Collectors.toList());

        gtfsTripRepository.deleteAll();
        gtfsTripRepository.saveAll(gtfsTrips);
    }

    private void createVrGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules) throws IOException {
        List<Schedule> vrPassengerAdhocSchedules = createVrSchedules(passengerAdhocSchedules);
        List<Schedule> vrPassengerRegularSchedules = createVrSchedules(passengerRegularSchedules);

        createGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules, "gtfs-vr.zip");
        createVRTreGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules);
    }

    private List<Schedule> createVrSchedules(List<Schedule> passengerAdhocSchedules) {
        Set<String> acceptedCommuterLineIds = Sets.newHashSet("R", "M", "T", "D", "G", "Z");
        List<Schedule> vrPassengerAdhocSchedules = new ArrayList<>();
        for (Schedule schedule : passengerAdhocSchedules) {
            filterOutNonStops(schedule);
            if (schedule.operator.operatorUICCode == 10 &&
                    (Strings.isNullOrEmpty(schedule.commuterLineId) || acceptedCommuterLineIds.contains(schedule.commuterLineId))) {
                vrPassengerAdhocSchedules.add(schedule);
            }
        }
        return vrPassengerAdhocSchedules;
    }

    private void filterOutNonStops(Schedule schedule) {
        List<ScheduleRow> filteredRows = new ArrayList<>();
        for (ScheduleRow scheduleRow : schedule.scheduleRows) {
            if (scheduleRow.arrival == null || scheduleRow.departure == null || (!scheduleRow.departure.timestamp.equals(scheduleRow.arrival.timestamp) && scheduleRow.departure.stopType != ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL)) {
                filteredRows.add(scheduleRow);
            }
        }
        schedule.scheduleRows = filteredRows;
    }


    public void createVRTreGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules) throws IOException {
        Set<String> includedStations = Sets.newHashSet("OV", "OVK", "LPÄ", "NOA");
        Predicate<Schedule> treFilter = schedule -> schedule.scheduleRows.stream().anyMatch(scheduleRow -> includedStations.contains(scheduleRow.station.stationShortCode));
        List<Schedule> vrTrePassengerAdhocSchedules = passengerAdhocSchedules.stream().filter(treFilter).collect(Collectors.toList());
        List<Schedule> vrTreRegularSchedules = passengerRegularSchedules.stream().filter(treFilter).collect(Collectors.toList());

        createGtfs(vrTrePassengerAdhocSchedules, vrTreRegularSchedules, "gtfs-vr-tre.zip");
    }

    private boolean isPassengerTrain(Schedule s) {
        return s.trainCategory.name.equals("Commuter") || (s.trainCategory.name.equals("Long-distance") && s.trainType.commercial == true);
    }
}
