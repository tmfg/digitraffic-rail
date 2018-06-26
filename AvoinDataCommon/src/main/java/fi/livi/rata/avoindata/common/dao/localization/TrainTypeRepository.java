package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainTypeRepository extends CustomGeneralRepository<TrainType, Long> {
}
