package fi.livi.rata.avoindata.common.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class DateProvider {
    public ZonedDateTime nowInHelsinki() {
        return ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));
    }

    public LocalDate dateInHelsinki() {
        return LocalDate.now(ZoneId.of("Europe/Helsinki"));
    }
}
