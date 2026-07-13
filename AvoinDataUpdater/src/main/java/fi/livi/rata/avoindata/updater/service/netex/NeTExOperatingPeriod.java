package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

/**
 * Represents a NeTEx OperatingPeriod with start and end dates.
 */
public class NeTExOperatingPeriod {
    private final String id;
    private final LocalDate fromDate;
    private final LocalDate toDate;

    public NeTExOperatingPeriod(final String id, final LocalDate fromDate, final LocalDate toDate) {
        this.id = id;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public String getId() {
        return id;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }
}
