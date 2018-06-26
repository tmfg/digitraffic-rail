package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainCategoryRepository extends CustomGeneralRepository<TrainCategory, Long> {
}
