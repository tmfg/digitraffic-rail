package fi.livi.rata.avoindata.common.dao.train;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.train.ExtractedSchedule;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractedScheduleRepository extends CustomGeneralRepository<ExtractedSchedule, Long> {

}
