package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import fi.livi.rata.avoindata.updater.service.stopsector.StopSectorService;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TrainLockExecutor trainLockExecutor;
    private final SingleDayScheduleExtractService singleDayScheduleExtractService;
    private final ScheduleProviderService scheduleProviderService;
    private final LastUpdateService lastUpdateService;

    private final StopSectorService stopSectorService;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Value("${updater.schedule-extracting.days-to-extract:365}")
    protected Integer numberOfDaysToExtract;

    @Value("${updater.trains.numberOfFutureDaysToInitialize}")
    protected Integer numberOfFutureDaysToInitialize;

    public ScheduleService(final TrainLockExecutor trainLockExecutor, final SingleDayScheduleExtractService singleDayScheduleExtractService, final ScheduleProviderService scheduleProviderService, final LastUpdateService lastUpdateService, final StopSectorService stopSectorService) {
        this.trainLockExecutor = trainLockExecutor;
        this.singleDayScheduleExtractService = singleDayScheduleExtractService;
        this.scheduleProviderService = scheduleProviderService;
        this.lastUpdateService = lastUpdateService;
        this.stopSectorService = stopSectorService;
    }

    @Scheduled(cron = "${updater.schedule-extracting.cron}", zone = "Europe/Helsinki")
    public synchronized void extractSchedules() {
        final StopWatch stopWatch = StopWatch.createStarted();
        final ZonedDateTime startDate = DateProvider.nowInHelsinki();
        log.info("Starting extract");

        try {
            final LocalDate start = DateProvider.dateInHelsinki().plusDays(numberOfFutureDaysToInitialize + 1);
            final LocalDate end = start.plusDays(numberOfDaysToExtract);

            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(start);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(start);

            log.info("Schedules fetched adHoc {} regular {}", adhocSchedules.size(), regularSchedules.size());

            for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
                final StopWatch stopWatchForDate = StopWatch.createStarted();
                final ZonedDateTime nowInHelsinki = DateProvider.nowInHelsinki();
                log.info("Extracting for date {}", date);

                if (stopWatch.getTime(TimeUnit.HOURS) < 3) { // Extraction should never cross dates: https://solitaoy.slack.com/archives/C033BR7RH54/p1661246597190849
                    extractForDate(adhocSchedules, regularSchedules, date);
                } else {
                    log.error("Stopping schedule extraction due to taking too long. Start: {}, Now: {}", startDate, nowInHelsinki);
                    return;
                }

                // sleep, so we don't block the train locker executor totally
                try {
                    // if it takes longer to extract, sleep a bit longer too
                    final var sleepTime = Math.max(1000, stopWatchForDate.getTime(TimeUnit.SECONDS) * 100);
                    Thread.sleep(sleepTime);
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            lastUpdateService.update(LastUpdateService.LastUpdatedType.FUTURE_TRAINS);
        } catch (final Exception e) {
            log.error("Error extracting schedules", e);
        } finally {
            log.info("Ending extract, tookMs={}", stopWatch.getDuration().toMillis());
        }
    }

    private void extractForDate(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules,
                                final LocalDate date) throws InterruptedException {

        final List<Train> extractedTrains = trainLockExecutor.executeInLock("ScheduleForDate",
                () -> singleDayScheduleExtractService.extract(adhocSchedules, regularSchedules, date, true));

        stopSectorService.addTrains(extractedTrains, "Schedule");

        throttle(extractedTrains.size());
    }

    /**
     * Sleep for a while so clients do not choke on new json.
     * Length of sleeping is dependant of the amount of trains extracted.
     */
    private void throttle(final int trainCount) throws InterruptedException {
        if(trainCount > 10) {
            Thread.sleep(trainCount > 400 ? 30000 : 10000);
        }
    }
}
