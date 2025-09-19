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
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS_DUMP;
import static fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType.TRAIN_RUNNING_MESSAGE_RULES;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class IsUpToDateService {

    @Autowired
    private LastUpdateService lastUpdateService;

    private final Map<LastUpdateService.LastUpdatedType, Duration> alarmLimits = new HashMap<>();

    public static class IsToUpToDateDto {
        public Instant lastUpdated;
        public Duration alarmLimit;
        public Duration durationSinceLastUpdate;
        public boolean isUpToDate;

        public IsToUpToDateDto(final Instant lastUpdated, final Duration alarmLimit, final Duration durationSinceLastUpdate) {
            this.lastUpdated = lastUpdated;
            this.alarmLimit = alarmLimit;
            this.durationSinceLastUpdate = durationSinceLastUpdate;
            this.isUpToDate = !alarmLimit.minus(durationSinceLastUpdate).isNegative();
        }
    }

    @PostConstruct
    private void setup() {
        // real time
        for (final LastUpdateService.LastUpdatedType value : LastUpdateService.LastUpdatedType.values()) {
            alarmLimits.put(value, Duration.ofMinutes(5));
        }

        alarmLimits.put(TRACK_WORK_NOTIFICATIONS, Duration.ofMinutes(10));
        alarmLimits.put(TRAFFIC_RESTRICTION_NOTIFICATIONS, Duration.ofMinutes(10));
        alarmLimits.put(LOCALIZATIONS, Duration.ofHours(25));
        alarmLimits.put(OPERATORS, Duration.ofHours(25));
        alarmLimits.put(TIME_TABLE_PERIODS, Duration.ofHours(25));
        alarmLimits.put(TRAIN_RUNNING_MESSAGE_RULES, Duration.ofHours(25));
        alarmLimits.put(TRACKSECTIONS, Duration.ofHours(25));
        alarmLimits.put(GTFS, Duration.ofHours(27));
        alarmLimits.put(FUTURE_TRAINS, Duration.ofHours(48));
        alarmLimits.put(OLD_TRAINS, Duration.ofHours(60));
        alarmLimits.put(STATIONS, Duration.ofHours(25));
        alarmLimits.put(CATEGORY_CODES, Duration.ofHours(25));
        alarmLimits.put(TRAIN_LOCATIONS_DUMP, Duration.ofHours(24 * 2));
    }



    public Map<LastUpdateService.LastUpdatedType, IsToUpToDateDto> getIsUpToDates() {
        final Map<LastUpdateService.LastUpdatedType, Instant> lastUpdateTimes = lastUpdateService.getLastUpdateTimes();

        final Map<LastUpdateService.LastUpdatedType, IsToUpToDateDto> result = new HashMap<>();

        final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        for (final LastUpdateService.LastUpdatedType value : LastUpdateService.LastUpdatedType.values()) {
            final Instant lastUpdated = lastUpdateTimes.get(value);
            final Duration alarmLimit = alarmLimits.get(value);
            if (lastUpdated != null && alarmLimit != null) {
                final Duration between = Duration.between(lastUpdated, now);
                final IsToUpToDateDto upToDate = new IsToUpToDateDto(lastUpdated, alarmLimit, between);
                result.put(value, upToDate);
            }
        }

        return result;
    }
}
