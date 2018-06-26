package fi.livi.rata.avoindata.updater.updaters.abstractup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.ZoneId;

public class InitializationPeriod {
    private static final Logger log = LoggerFactory.getLogger(InitializationPeriod.class);

    private final String propertyPrefix;

    public InitializationPeriod(Environment environment, String propertyPrefix) {
        this.propertyPrefix = propertyPrefix;

        Integer numberOfDaysToInitializeInLockedMode = getProperty(environment, "numberOfPastDaysToInitializeInLockedMode");
        Integer numberOfFutureDaysToInitialize = getProperty(environment, "numberOfFutureDaysToInitialize");
        Integer numberOfDaysToInitialize = getProperty(environment, "numberOfPastDaysToInitialize");

        final LocalDate startDate = LocalDate.now(ZoneId.of("Europe/Helsinki"));

        firstDateInLockMode = startDate.minusDays(numberOfDaysToInitializeInLockedMode);
        lastDateInLockMode = startDate.plusDays(numberOfFutureDaysToInitialize);

        endNonLockDate = startDate.minusDays(numberOfDaysToInitialize);

        log.info("Initializing {} from {} -> {} -> {}. Numbers: {} -> {} -> {}", propertyPrefix, lastDateInLockMode, firstDateInLockMode,
                endNonLockDate, numberOfFutureDaysToInitialize, numberOfDaysToInitializeInLockedMode, numberOfDaysToInitialize);
    }

    private Integer getProperty(Environment environment, final String propertySuffix) {
        return environment.getProperty(String.format("%s.%s", propertyPrefix, propertySuffix), Integer.class);
    }

    public LocalDate firstDateInLockMode;
    public LocalDate lastDateInLockMode;
    public LocalDate endNonLockDate;
}