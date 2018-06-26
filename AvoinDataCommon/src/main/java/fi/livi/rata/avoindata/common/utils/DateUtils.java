package fi.livi.rata.avoindata.common.utils;


import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class DateUtils {
    public static boolean isInclusivelyBetween(LocalDate date, LocalDate start, LocalDate end) {
        LocalDate actualEndDate = end != null ? end : LocalDate.MAX;

        final boolean isEqualOrAfterStart = date.isAfter(start) || date.equals(start);
        final boolean isEqualOrBeforeEnd = date.isBefore(actualEndDate) || date.equals(actualEndDate);
        return isEqualOrAfterStart && isEqualOrBeforeEnd;
    }

}
