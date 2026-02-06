package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.common.utils.DateUtils;

import java.time.LocalDate;

public class DateRange {
    public final LocalDate startDate;
    public final LocalDate endDate;

    public DateRange(final LocalDate startDate, final LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean isInclusivelyBetween(final LocalDate candidate) {
        return DateUtils.isInclusivelyBetween(candidate, startDate, endDate);
    }
}
