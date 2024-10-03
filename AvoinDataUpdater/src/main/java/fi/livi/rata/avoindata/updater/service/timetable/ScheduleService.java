package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
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
        final StopWatch stopWatch = StopWatch.createStarted();
        final ZonedDateTime startDate = dp.nowInHelsinki();
        log.info("Starting extract");

        try {
            final LocalDate start = dp.dateInHelsinki().plusDays(numberOfFutureDaysToInitialize + 1);
            final LocalDate end = start.plusDays(numberOfDaysToExtract);

            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(start);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(start);

            log.info("Schedules fetched adHoc {} regular {}", adhocSchedules.size(), regularSchedules.size());

            for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
                final ZonedDateTime nowInHelsinki = dp.nowInHelsinki();
                log.info("Extracting for date {}", date);

                if (stopWatch.getTime(TimeUnit.HOURS) < 3) { // Extraction should never cross dates: https://solitaoy.slack.com/archives/C033BR7RH54/p1661246597190849
                    extractForDate(adhocSchedules, regularSchedules, date);
                } else {
                    log.error("Stopping schedule extraction due to taking too long. Start: {}, Now: {}", startDate, nowInHelsinki);
                    return;
                }
            }

            lastUpdateService.update(LastUpdateService.LastUpdatedType.FUTURE_TRAINS);
        } catch (final Exception e) {
            log.error("Error extracting schedules", e);
        } finally {
            log.info("Ending extract, tookMs={}", stopWatch.getTime());
        }
    }

    private void extractForDate(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules,
                                final LocalDate date) throws InterruptedException {

        final List<Train> extractedTrains = trainLockExecutor.executeInLock("ScheduleForDate",
                () -> singleDayScheduleExtractService.extract(adhocSchedules, regularSchedules, date, true));

        throttle(extractedTrains.size());
    }

    /**
     * Sleep for a while so clients do not choke on new json.
     * Length of sleeping is dependant of the amount of trains extracted.
     */
    private void throttle(final int trainCount) throws InterruptedException {
        if(trainCount > 0) {
            Thread.sleep(trainCount > 400 ? 30000 : 10000);
        }
    }
}
