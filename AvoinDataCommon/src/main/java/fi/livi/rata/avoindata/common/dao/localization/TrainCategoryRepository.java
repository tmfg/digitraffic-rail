package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainCategoryRepository extends CustomGeneralRepository<TrainCategory, Long> {
    @Cacheable("trainCategorys")
    @Query("select t from TrainCategory  t where t.id = ?1")
    Optional<TrainCategory> findByIdCached(Long id);

    @Cacheable("trainCategorys")
    @Query("select t from TrainCategory  t where t.name in ?1")
    List<TrainCategory> findByNameCached(List<String> names);

    @Cacheable("trainCategorys")
    @Query("select t from TrainCategory t")
    List<TrainCategory> findAllCached();
}
