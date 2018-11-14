package fi.livi.rata.avoindata.common.dao.metadata;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface TimeTablePeriodRepository extends CustomGeneralRepository<TimeTablePeriod, Long> {
    @Query("select ttp from TimeTablePeriod  ttp inner join fetch ttp.changeDates cd order by ttp.effectiveFrom desc,cd.effectiveFrom desc")
    Set<TimeTablePeriod> getTimeTablePeriods();

}