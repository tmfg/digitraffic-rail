package fi.livi.rata.avoindata.common.utils;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class DateUtils {
    public static boolean isInclusivelyBetween(final LocalDate date, final LocalDate start, final LocalDate end) {
        if(date.isBefore(start)) {
            return false;
        }

        return end == null || !date.isAfter(end);
    }
}
