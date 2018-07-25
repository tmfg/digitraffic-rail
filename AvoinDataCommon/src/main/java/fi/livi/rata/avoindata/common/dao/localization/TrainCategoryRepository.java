package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainCategoryRepository extends CustomGeneralRepository<TrainCategory, Long> {
    @Cacheable("trainCategorys")
    @Query("select t from TrainCategory  t where t.id = ?1")
    TrainCategory findByIdCached(Long id);
}
