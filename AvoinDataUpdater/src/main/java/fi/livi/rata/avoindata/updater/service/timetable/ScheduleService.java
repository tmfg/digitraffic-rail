package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class ScheduleService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SingleDayScheduleExtractService singleDayScheduleExtractService;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Value("${updater.schedule-extracting.days-to-extract:365}")
    protected Integer numberOfDaysToExtract;

    @Value("${updater.trains.numberOfFutureDaysToInitialize}")
    protected Integer numberOfFutureDaysToInitialize;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private DateProvider dp;

    @Autowired
    private ScheduleProviderService scheduleProviderService;

    @Autowired
    private LastUpdateService lastUpdateService;

    @Scheduled(cron = "${updater.schedule-extracting.cron}", zone = "Europe/Helsinki")
    public synchronized void extractSchedules() {
        try {
            final LocalDate start = dp.dateInHelsinki().plusDays(numberOfFutureDaysToInitialize + 1);
            final LocalDate end = start.plusDays(numberOfDaysToExtract);

            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(start);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(start);

            for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
                extractForDate(adhocSchedules, regularSchedules, date);
            }

            lastUpdateService.update(LastUpdateService.LastUpdatedType.FUTURE_TRAINS);
        } catch (Exception e) {
            log.error("Error extracting schedules", e);
        }

    }

    private void extractForDate(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules,
                                final LocalDate date) throws InterruptedException {
        List<Schedule> allSchedules = new ArrayList<Schedule>(adhocSchedules);
        allSchedules.addAll(regularSchedules);

        final LocalDate finalDate = date;
        final List<Train> extractedTrains = trainLockExecutor.executeInLock(
                () -> singleDayScheduleExtractService.extract(allSchedules, finalDate, true));

        if (!extractedTrains.isEmpty()) {
            //Sleep for a while so clients do not choke on new json
            Thread.sleep(60000 * 2);
        }
    }
}
