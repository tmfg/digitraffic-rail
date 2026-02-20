package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.common.utils.DateUtils;

import java.time.LocalDate;

public record DateRange(LocalDate startDate, LocalDate endDate) {

    public boolean isInclusivelyBetween(final LocalDate candidate) {
        return DateUtils.isInclusivelyBetween(candidate, startDate, endDate);
    }
}
