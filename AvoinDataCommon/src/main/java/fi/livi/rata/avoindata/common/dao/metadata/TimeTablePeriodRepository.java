package fi.livi.rata.avoindata.common.dao.metadata;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeTablePeriodRepository extends CustomGeneralRepository<TimeTablePeriod, Long> {

}