package fi.livi.rata.avoindata.server.controller.utils;

import java.time.LocalDate;
import java.util.Collection;
import jakarta.servlet.http.HttpServletResponse;

public class CacheControl {

    public static final int CACHE_AGE_DAY = (24 * 60 * 60);

    public final int WITH_CHANGENUMBER_NO_RESULT;
    public final int WITH_CHANGENUMBER_RESULT;
    public final int WITHOUT_CHANGENUMBER_NO_RESULT;
    public final int WITHOUT_CHANGENUMBER_RESULT;

    public CacheControl(int withChangenumberNoResult, int withChangenumberResult, int withoutChangenumberNoResult,
            int withoutChangenumberResult) {
        this.WITH_CHANGENUMBER_NO_RESULT = withChangenumberNoResult;
        this.WITH_CHANGENUMBER_RESULT = withChangenumberResult;
        this.WITHOUT_CHANGENUMBER_NO_RESULT = withoutChangenumberNoResult;
        this.WITHOUT_CHANGENUMBER_RESULT = withoutChangenumberResult;
    }

    public static void setCacheMaxAgeSeconds(HttpServletResponse response, int maxAge) {
        response.setHeader("Cache-Control", String.format("max-age=%d, public", maxAge));
    }

    public static void clearCacheMaxAgeSeconds(HttpServletResponse response) {
        response.setHeader("Cache-Control", "");
    }

    public void setCacheParameter(HttpServletResponse response, Collection<?> items) {
        setCacheParameter(response, items, -1);
    }

    public void setCacheParameter(HttpServletResponse response, Collection<?> items, long change_number) {
        if (response == null) {
            return;
        }

        if (change_number > 0) {
            if (items.isEmpty()) {
                setCacheMaxAgeSeconds(response, WITH_CHANGENUMBER_NO_RESULT);
            } else {
                setCacheMaxAgeSeconds(response, WITH_CHANGENUMBER_RESULT);
            }
        } else {
            if (items.isEmpty()) {
                setCacheMaxAgeSeconds(response, WITHOUT_CHANGENUMBER_NO_RESULT);
            } else {
                setCacheMaxAgeSeconds(response, WITHOUT_CHANGENUMBER_RESULT);
            }
        }
    }

    public static void addSchedulesCacheParametersForDailyResult(final LocalDate departure_date, final HttpServletResponse response) {
        if (response == null) {
            return;
        }

        final LocalDate TODAY = LocalDate.now();

        if (departure_date.equals(TODAY)) {
            // Kuluva päivä
            setCacheMaxAgeSeconds(response, 15 * 60);
        } else if (departure_date.equals(TODAY.plusDays(1))) {
            //Huominen
            setCacheMaxAgeSeconds(response, 15 * 60);
        } else if (departure_date.isAfter(TODAY.plusDays(1))) {
            // Ylihuomenna tai myöhemmin
            setCacheMaxAgeSeconds(response, 60 * 60);
        } else if (departure_date.equals(TODAY.minusDays(1))) {
            //Eilinen
            setCacheMaxAgeSeconds(response, 60 * 60);
        } else {
            // Toissapäivänä tai aikaisemmin
            setCacheMaxAgeSeconds(response, (1 * CACHE_AGE_DAY));
        }
    }

    public static void addHistoryCacheParametersForDailyResult(final LocalDate departure_date, final HttpServletResponse response) {
        if (response == null) {
            return;
        }

        final LocalDate TODAY = LocalDate.now();

        if (departure_date.equals(TODAY)) {
            //Tänään
            setCacheMaxAgeSeconds(response, 5 * 60);
        } else if (departure_date.equals(TODAY.minusDays(1))) {
            //Eilen
            setCacheMaxAgeSeconds(response, 60 * 60);
        } else if (departure_date.isBefore(TODAY.minusDays(1))) {
            //Sitä myöhemmin
            setCacheMaxAgeSeconds(response, 60 * 60 * 24);
        } else {
            //Muulloin (tulevaisuus)
            setCacheMaxAgeSeconds(response, 60 * 60);
        }
    }
}
