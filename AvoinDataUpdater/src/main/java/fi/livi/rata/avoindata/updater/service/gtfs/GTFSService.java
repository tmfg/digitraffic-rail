package fi.livi.rata.avoindata.updater.service.gtfs;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

@Service
public class GTFSService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static int FIRST_STOP_SEQUENCE = 1;

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

    @Scheduled(cron = "${updater.gtfs.cron}", zone = "UTC")
    public void generateGTFS() {
        TimingUtil.log(log, "generateGTFS", () -> {
            try {
                final LocalDate start = dp.dateInHelsinki().minusDays(7);
                this.generateGTFS(scheduleProviderService.getAdhocSchedules(start), scheduleProviderService.getRegularSchedules(start));

                lastUpdateService.update(LastUpdateService.LastUpdatedType.GTFS);
            } catch (final ExecutionException | InterruptedException | IOException e) {
                log.error("Error generating gtfs", e);

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

    public GTFSDto createGtfs(final List<Schedule> passengerAdhocSchedules,
                              final List<Schedule> passengerRegularSchedules,
                              final String zipFileName,
                              final boolean filterOutNonStops) throws IOException {
        final GTFSDto gfsDto = gtfsEntityService.createGTFSEntity(passengerAdhocSchedules, passengerRegularSchedules);

        if (filterOutNonStops) {
            for (final Trip trip : gfsDto.trips) {
                trip.stopTimes = this.filterOutNonStops(trip.stopTimes);
            }
        }

        gtfsWritingService.writeGTFSFiles(gfsDto, zipFileName);

        return gfsDto;
    }

    public void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) throws IOException {
        final GTFSDto gtfs = this.createGtfs(adhocSchedules, regularSchedules, "gtfs-all.zip", false);

        final List<Schedule> passengerAdhocSchedules = Lists.newArrayList(
                Collections2.filter(adhocSchedules, this::isPassengerTrain));
        final List<Schedule> passengerRegularSchedules = Lists.newArrayList(
                Collections2.filter(regularSchedules, this::isPassengerTrain));

        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger.zip", false);
        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger-stops.zip", true);
        createVrGtfs(passengerAdhocSchedules, passengerRegularSchedules);

        gtfsTripService.updateGtfsTrips(gtfs);

        log.info("Successfully wrote GTFS files");
    }

    private List<StopTime> filterOutNonStops(final List<StopTime> stopTimes) {
        final List<StopTime> filteredStopTimes = new ArrayList<>();

        int stopSequence = FIRST_STOP_SEQUENCE;
        for (int i = 0; i < stopTimes.size(); i++) {
            final StopTime stopTime = stopTimes.get(i);

            final ScheduleRow scheduleRow = stopTime.source;

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

    private void createVrGtfs(final List<Schedule> passengerAdhocSchedules, final List<Schedule> passengerRegularSchedules) throws IOException {
        final List<Schedule> vrPassengerAdhocSchedules = createVrSchedules(passengerAdhocSchedules);
        final List<Schedule> vrPassengerRegularSchedules = createVrSchedules(passengerRegularSchedules);

        createGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules, "gtfs-vr.zip", true);
        createVRTreGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules);
    }

    private List<Schedule> createVrSchedules(final List<Schedule> passengerAdhocSchedules) {
        final Set<String> acceptedCommuterLineIds = Sets.newHashSet("R", "M", "T", "D", "G", "Z", "O", "H");
        final List<Schedule> vrPassengerAdhocSchedules = new ArrayList<>();
        for (final Schedule schedule : passengerAdhocSchedules) {
            if (schedule.operator.operatorUICCode == 10 &&
                    (Strings.isNullOrEmpty(schedule.commuterLineId) || acceptedCommuterLineIds.contains(schedule.commuterLineId))) {
                vrPassengerAdhocSchedules.add(schedule);
            }
        }
        return vrPassengerAdhocSchedules;
    }


    public void createVRTreGtfs(final List<Schedule> passengerAdhocSchedules, final List<Schedule> passengerRegularSchedules) throws IOException {
        final Set<String> includedStations = Sets.newHashSet("OV", "OVK", "LPÄ", "NOA");
        final Predicate<Schedule> treFilter = schedule -> schedule.scheduleRows.stream().anyMatch(scheduleRow -> includedStations.contains(scheduleRow.station.stationShortCode));
        final List<Schedule> vrTrePassengerAdhocSchedules = passengerAdhocSchedules.stream().filter(treFilter).collect(Collectors.toList());
        final List<Schedule> vrTreRegularSchedules = passengerRegularSchedules.stream().filter(treFilter).collect(Collectors.toList());

        createGtfs(vrTrePassengerAdhocSchedules, vrTreRegularSchedules, "gtfs-vr-tre.zip", true);
    }

    private boolean isPassengerTrain(final Schedule s) {
        return (s.trainCategory.name.equals("Commuter") || (s.trainCategory.name.equals("Long-distance") && s.trainType.commercial)) && !Sets.newHashSet("V", "HV", "MV").contains(s.trainType.name);
    }
}
