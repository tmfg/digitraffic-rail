package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Service
public class TimeTableRowService {

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    public List<SimpleTimeTableRow> getNextTenDays() {
        final ZonedDateTime currentDateTime = DateProvider.nowInHelsinki();
        return timeTableRowRepository.
                findSimpleByScheduledTimeBetween(
                        currentDateTime.minusDays(1).toLocalDate(),
                        currentDateTime.plusDays(10).toLocalDate(),
                        currentDateTime,
                        currentDateTime.plusDays(10));
    }

    /** Rows of one operating day, whose journeys may run well past midnight. */
    public List<SimpleTimeTableRow> getDay(final LocalDate day) {
        final ZonedDateTime dayStart = day.atStartOfDay(ZoneId.of("Europe/Helsinki"));
        return timeTableRowRepository.
                findSimpleByScheduledTimeBetween(
                        day,
                        day,
                        dayStart.minusDays(1),
                        dayStart.plusDays(2));
    }

}
