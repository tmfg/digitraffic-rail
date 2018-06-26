package fi.livi.rata.avoindata.common.dao.train;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeTableRowRepository extends CustomGeneralRepository<TimeTableRow, TimeTableRowId> {
}
