package fi.livi.rata.avoindata.common.dao.localization;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;

@Repository
public interface TrainTypeRepository extends CustomGeneralRepository<TrainType, Long> {
    @Cacheable("trainTypes")
    @Query("select t from TrainType  t where t.id = ?1")
    Optional<TrainType> findByIdCached(Long id);
}
