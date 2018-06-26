package fi.livi.rata.avoindata.common.dao.composition;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.composition.CompositionTimeTableRow;
import org.springframework.stereotype.Repository;

@Repository
public interface CompositionTimeTableRowRepository extends CustomGeneralRepository<CompositionTimeTableRow, Long> {
}
