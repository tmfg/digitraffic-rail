package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.PowerType;
import org.springframework.stereotype.Repository;

@Repository
public interface PowerTypeRepository extends CustomGeneralRepository<PowerType, Long> {

    PowerType findByAbbreviation(String abbreviation);
}
