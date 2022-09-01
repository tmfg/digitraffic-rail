package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class GTFSService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

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

    @Autowired
    private GTFSTripService gtfsTripService;

    @Scheduled(cron = "${updater.gtfs.cron}", zone = "Europe/Helsinki")
    public void generateGTFS() {
        TimingUtil.log(log, "generateGTFS", () -> {
            try {
                final LocalDate start = dp.dateInHelsinki().minusDays(7);
                this.generateGTFS(scheduleProviderService.getAdhocSchedules(start), scheduleProviderService.getRegularSchedules(start));

                lastUpdateService.update(LastUpdateService.LastUpdatedType.GTFS);
            } catch (final ExecutionException | InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
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

    public GTFSDto createGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules, String zipFileName, boolean filterOutNonStops) throws IOException {
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

        if (filterOutNonStops) {
            for (Trip trip : gfsDto.trips) {
                trip.stopTimes = this.filterOutNonStops(trip.stopTimes);
            }
        }

        gtfsWritingService.writeGTFSFiles(gfsDto, zipFileName);

        return gfsDto;
    }

    public void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) throws IOException {
        final GTFSDto gtfs = this.createGtfs(adhocSchedules, regularSchedules, "gtfs-all.zip", false);

        final List<Schedule> passengerAdhocSchedules = Lists.newArrayList(
                Collections2.filter(adhocSchedules, s -> isPassengerTrain(s)));
        final List<Schedule> passengerRegularSchedules = Lists.newArrayList(
                Collections2.filter(regularSchedules, s -> isPassengerTrain(s)));

        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger.zip", false);
        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger-stops.zip", true);
        createVrGtfs(passengerAdhocSchedules, passengerRegularSchedules);

        gtfsTripService.updateGtfsTrips(gtfs);

        log.info("Successfully wrote GTFS files");
    }

    private List<StopTime> filterOutNonStops(final List<StopTime> stopTimes) {
        final List<StopTime> filteredStopTimes = new ArrayList<>();

        int stopSequence = 1;
        for (int i = 0; i < stopTimes.size(); i++) {
            StopTime stopTime = stopTimes.get(i);

            ScheduleRow scheduleRow = stopTime.source;

            final boolean isLongStop =
                    scheduleRow.departure == null || scheduleRow.arrival == null ||
                            (!stopTime.departureTime.equals(stopTime.arrivalTime) && (scheduleRow.arrival.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL || scheduleRow.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL));

            if (isLongStop) {
                filteredStopTimes.add(stopTime);
                stopTime.stopSequence = stopSequence++;
            }
        }

        return filteredStopTimes;
    }

    private void createVrGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules) throws IOException {
        List<Schedule> vrPassengerAdhocSchedules = createVrSchedules(passengerAdhocSchedules);
        List<Schedule> vrPassengerRegularSchedules = createVrSchedules(passengerRegularSchedules);

        createGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules, "gtfs-vr.zip", true);
        createVRTreGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules);
    }

    private List<Schedule> createVrSchedules(List<Schedule> passengerAdhocSchedules) {
        Set<String> acceptedCommuterLineIds = Sets.newHashSet("R", "M", "T", "D", "G", "Z", "O");
        List<Schedule> vrPassengerAdhocSchedules = new ArrayList<>();
        for (Schedule schedule : passengerAdhocSchedules) {
            if (schedule.operator.operatorUICCode == 10 &&
                    (Strings.isNullOrEmpty(schedule.commuterLineId) || acceptedCommuterLineIds.contains(schedule.commuterLineId))) {
                vrPassengerAdhocSchedules.add(schedule);
            }
        }
        return vrPassengerAdhocSchedules;
    }


    public void createVRTreGtfs(List<Schedule> passengerAdhocSchedules, List<Schedule> passengerRegularSchedules) throws IOException {
        Set<String> includedStations = Sets.newHashSet("OV", "OVK", "LPÄ", "NOA");
        Predicate<Schedule> treFilter = schedule -> schedule.scheduleRows.stream().anyMatch(scheduleRow -> includedStations.contains(scheduleRow.station.stationShortCode));
        List<Schedule> vrTrePassengerAdhocSchedules = passengerAdhocSchedules.stream().filter(treFilter).collect(Collectors.toList());
        List<Schedule> vrTreRegularSchedules = passengerRegularSchedules.stream().filter(treFilter).collect(Collectors.toList());

        createGtfs(vrTrePassengerAdhocSchedules, vrTreRegularSchedules, "gtfs-vr-tre.zip", true);
    }

    private boolean isPassengerTrain(Schedule s) {
        return (s.trainCategory.name.equals("Commuter") || (s.trainCategory.name.equals("Long-distance") && s.trainType.commercial == true)) && Sets.newHashSet("V", "HV", "MV").contains(s.trainType.name) == false;
    }
}
