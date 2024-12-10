package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Repository
public interface TimeTableRowRepository extends CustomGeneralRepository<TimeTableRow, TimeTableRowId> {

    @Query("""
            SELECT sttr FROM SimpleTimeTableRow sttr \
            WHERE sttr.departureDate BETWEEN :departureDateStart AND :departureDateEnd AND \
            sttr.scheduledTime > :scheduledTimeStart AND sttr.scheduledTime < :scheduledTimeEnd""")
    List<SimpleTimeTableRow> findSimpleByScheduledTimeBetween(
            @Param("departureDateStart")
            LocalDate departureDateStart,
            @Param("departureDateEnd")
            LocalDate departureDateEnd,
            @Param("scheduledTimeStart")
            ZonedDateTime scheduledTimeStart,
            @Param("scheduledTimeEnd")
            ZonedDateTime scheduledTimeEnd);

    @Query("""
            SELECT sttr \
            FROM SimpleTimeTableRow sttr \
            WHERE sttr.id.trainNumber = :trainNumber AND \
            sttr.departureDate = :departureDate AND \
            sttr.scheduledTime = :scheduledTime AND \
            sttr.stationShortCode = UPPER(:stationShortCode) AND \
            sttr.type = :type""")
    Optional<SimpleTimeTableRow> findSimpleBy(
            @Param("trainNumber")
            final long trainNumber,
            @Param("departureDate")
            final LocalDate departureDate,
            @Param("scheduledTime")
            final ZonedDateTime scheduledTime,
            @Param("stationShortCode")
            final String stationShortCode,
            @Param("type")
            final TimeTableRow.TimeTableRowType type);
}
