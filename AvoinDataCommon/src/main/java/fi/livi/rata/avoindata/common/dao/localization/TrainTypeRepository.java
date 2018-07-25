package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainTypeRepository extends CustomGeneralRepository<TrainType, Long> {
    @Cacheable("trainTypes")
    @Query("select t from TrainType  t where t.id = ?1")
    TrainType findByIdCached(Long id);
}
