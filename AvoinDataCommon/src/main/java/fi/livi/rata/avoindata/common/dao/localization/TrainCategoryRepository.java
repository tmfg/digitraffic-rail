package fi.livi.rata.avoindata.common.dao.localization;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;

@Repository
public interface TrainCategoryRepository extends CustomGeneralRepository<TrainCategory, Long> {
    @Cacheable("trainCategorys")
    @Query("select t from TrainCategory  t where t.id = ?1")
    Optional<TrainCategory> findByIdCached(Long id);
}
