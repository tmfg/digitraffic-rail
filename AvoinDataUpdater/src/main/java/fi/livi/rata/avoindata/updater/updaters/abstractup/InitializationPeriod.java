package fi.livi.rata.avoindata.updater.updaters.abstractup;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import fi.livi.rata.avoindata.common.utils.DateProvider;

public class InitializationPeriod {
    private static final Logger log = LoggerFactory.getLogger(InitializationPeriod.class);

    private final String propertyPrefix;

    public InitializationPeriod(final Environment environment, final String propertyPrefix) {
        this.propertyPrefix = propertyPrefix;

        final Integer numberOfDaysToInitializeInLockedMode = getProperty(environment, "numberOfPastDaysToInitializeInLockedMode");
        final Integer numberOfFutureDaysToInitialize = getProperty(environment, "numberOfFutureDaysToInitialize");
        final Integer numberOfDaysToInitialize = getProperty(environment, "numberOfPastDaysToInitialize");

        final LocalDate startDate = DateProvider.dateInHelsinki();

        firstDateInLockMode = startDate.minusDays(numberOfDaysToInitializeInLockedMode);
        lastDateInLockMode = startDate.plusDays(numberOfFutureDaysToInitialize);

        endNonLockDate = startDate.minusDays(numberOfDaysToInitialize);

        log.info("method=InitializationPeriod prefix={} lastDateInLockMode={} -> firstDateInLockMode={} -> endNonLockDate={}. Numbers: numberOfFutureDaysToInitialize={} -> numberOfDaysToInitializeInLockedMode={} -> numberOfDaysToInitialize={}",
                propertyPrefix, lastDateInLockMode, firstDateInLockMode,
                endNonLockDate, numberOfFutureDaysToInitialize, numberOfDaysToInitializeInLockedMode, numberOfDaysToInitialize);
    }

    private Integer getProperty(final Environment environment, final String propertySuffix) {
        return environment.getProperty(String.format("%s.%s", propertyPrefix, propertySuffix), Integer.class);
    }

    public LocalDate firstDateInLockMode;
    public LocalDate lastDateInLockMode;
    public LocalDate endNonLockDate;
}