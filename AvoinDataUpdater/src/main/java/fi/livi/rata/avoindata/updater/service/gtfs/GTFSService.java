package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class GTFSService {
    @Autowired
    private GTFSEntityService gtfsEntityService;

    @Autowired
    private GTFSWritingService gtfsWritingService;

    @Autowired
    private GTFSTrainTypeService gtfsTrainTypeService;

    @Autowired
    private DateProvider dp;

    @Autowired
    private ScheduleProviderService scheduleProviderService;

    @Scheduled(cron = "${updater.gtfs.cron}", zone = "Europe/Helsinki")
    public void generateGTFS() throws ExecutionException, InterruptedException, IOException {
        final LocalDate start = dp.dateInHelsinki().minusDays(7);

        this.generateGTFS(scheduleProviderService.getAdhocSchedules(start), scheduleProviderService.getRegularSchedules(start));
    }

    public void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) throws IOException {
        GTFSDto allGtfsDto = gtfsEntityService.createGTFSEntity(adhocSchedules, regularSchedules);
        gtfsWritingService.writeGTFSFiles(allGtfsDto, "gtfs-all.zip");

        final List<Schedule> passengerAdhocSchedules = Lists.newArrayList(
                Collections2.filter(adhocSchedules, s -> gtfsTrainTypeService.getGtfsTrainType(s) != 1700));
        final List<Schedule> passengerRegularSchedules = Lists.newArrayList(
                Collections2.filter(regularSchedules, s -> gtfsTrainTypeService.getGtfsTrainType(s) != 1700));

        GTFSDto passengerGtfsDto = gtfsEntityService.createGTFSEntity(passengerAdhocSchedules, passengerRegularSchedules);
        gtfsWritingService.writeGTFSFiles(passengerGtfsDto, "gtfs-passenger.zip");
    }
}
