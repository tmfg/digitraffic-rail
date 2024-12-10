package fi.livi.rata.avoindata.common.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateProvider {

    private DateProvider() {
        // No init
        throw new IllegalStateException("DateProvider is not allowed to be instantiated");
    }

    public static final ZoneId ZONE_ID_HKI = ZoneId.of("Europe/Helsinki");

    public static ZonedDateTime nowInHelsinki() {
        return ZonedDateTime.now(ZONE_ID_HKI);
    }

    public static LocalDate dateInHelsinki() {
        return LocalDate.now(ZONE_ID_HKI);
    }
}
