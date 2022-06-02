package fi.livi.rata.avoindata.common.dao.train;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Repository
public interface TimeTableRowRepository extends CustomGeneralRepository<TimeTableRow, TimeTableRowId> {

    @Query("SELECT sttr FROM SimpleTimeTableRow sttr " +
            "WHERE sttr.scheduledTime > ?1 AND sttr.scheduledTime < ?2  " +
            "AND sttr.commercialTrack IS NOT NULL AND sttr.commercialTrack <> ''")
    List<SimpleTimeTableRow> findSimpleByScheduledTimeBetween(ZonedDateTime start, ZonedDateTime end);
}
