package fi.livi.rata.avoindata.updater.service.isuptodate;

import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.CATEGORY_CODES;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.FUTURE_TRAINS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.GTFS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.LOCALIZATIONS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.OLD_TRAINS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.OPERATORS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.STATIONS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TIME_TABLE_PERIODS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRACKSECTIONS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRACK_WORK_NOTIFICATIONS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRAFFIC_RESTRICTION_NOTIFICATIONS;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRAIN_RUNNING_MESSAGE_RULES;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IsUpToDateService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private LastUpdateService lastUpdateService;

    private Map<LastUpdateService.LastUpdatedType, Duration> alarmLimits = new HashMap<>();

    public static class IsToUpToDateDto {
        public ZonedDateTime lastUpdated;
        public Duration alarmLimit;
        public Duration durationSinceLastUpdate;
        public boolean isUpToDate;

        public IsToUpToDateDto(ZonedDateTime lastUpdated, Duration alarmLimit, Duration durationSinceLastUpdate) {
            this.lastUpdated = lastUpdated;
            this.alarmLimit = alarmLimit;
            this.durationSinceLastUpdate = durationSinceLastUpdate;
            this.isUpToDate = !alarmLimit.minus(durationSinceLastUpdate).isNegative();
        }
    }

    @PostConstruct
    private void setup() {
        // real time
        for (LastUpdateService.LastUpdatedType value : LastUpdateService.LastUpdatedType.values()) {
            alarmLimits.put(value, Duration.ofMinutes(5));
        }

        alarmLimits.put(TRACK_WORK_NOTIFICATIONS, Duration.ofMinutes(10));
        alarmLimits.put(TRAFFIC_RESTRICTION_NOTIFICATIONS, Duration.ofMinutes(10));
        alarmLimits.put(LOCALIZATIONS, Duration.ofHours(25));
        alarmLimits.put(OPERATORS, Duration.ofHours(25));
        alarmLimits.put(TIME_TABLE_PERIODS, Duration.ofHours(25));
        alarmLimits.put(TRAIN_RUNNING_MESSAGE_RULES, Duration.ofHours(25));
        alarmLimits.put(TRACKSECTIONS, Duration.ofHours(25));
        alarmLimits.put(GTFS, Duration.ofHours(25));
        alarmLimits.put(FUTURE_TRAINS, Duration.ofHours(48));
        alarmLimits.put(OLD_TRAINS, Duration.ofHours(36));
        alarmLimits.put(STATIONS, Duration.ofHours(25));
        alarmLimits.put(CATEGORY_CODES, Duration.ofHours(25));
    }

    public Map<LastUpdateService.LastUpdatedType, IsToUpToDateDto> getIsUpToDates() {
        Map<LastUpdateService.LastUpdatedType, ZonedDateTime> lastUpdateTimes = lastUpdateService.getLastUpdateTimes();

        Map<LastUpdateService.LastUpdatedType, IsToUpToDateDto> result = new HashMap<>();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        for (LastUpdateService.LastUpdatedType value : LastUpdateService.LastUpdatedType.values()) {
            ZonedDateTime lastUpdated = lastUpdateTimes.get(value);
            Duration alarmLimit = alarmLimits.get(value);
            if (lastUpdated != null && alarmLimit != null) {
                Duration between = Duration.between(lastUpdated, now);
                IsToUpToDateDto upToDate = new IsToUpToDateDto(lastUpdated, alarmLimit, between);
                result.put(value, upToDate);

                if (!upToDate.isUpToDate) {
                    log.error("{} was not up to date. Last updated: {}, limit: {}, between: {}", value, lastUpdated, alarmLimit, between);
                }
            }
        }

        return result;
    }
}
