package fi.livi.rata.avoindata.common.dao.train;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Repository
public interface TimeTableRowRepository extends CustomGeneralRepository<TimeTableRow, TimeTableRowId> {

    List<TimeTableRow> findByScheduledTimeBetween(ZonedDateTime start, ZonedDateTime end);
}
