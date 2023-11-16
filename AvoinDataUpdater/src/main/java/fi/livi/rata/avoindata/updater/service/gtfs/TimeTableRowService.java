package fi.livi.rata.avoindata.updater.service.gtfs;

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

    @Autowired
    private DateProvider dp;

    public List<SimpleTimeTableRow> getNextTenDays() {
        final ZonedDateTime currentDateTime = dp.nowInHelsinki();
        return timeTableRowRepository.
                findSimpleByScheduledTimeBetween(
                        currentDateTime.minusDays(1).toLocalDate(),
                        currentDateTime.plusDays(10).toLocalDate(),
                        currentDateTime,
                        currentDateTime.plusDays(10));
    }

}
