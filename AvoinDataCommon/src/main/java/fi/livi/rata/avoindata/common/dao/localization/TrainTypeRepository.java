package fi.livi.rata.avoindata.common.dao.localization;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;

@Repository
public interface TrainTypeRepository extends CustomGeneralRepository<TrainType, Long> {
    @Cacheable("trainTypes")
    @Query("select t from TrainType t where t.id = ?1")
    Optional<TrainType> findByIdCached(final Long id);

    // Note: used findFirstBy because inner join can fetch multiple lines
    // and they are all the same excluding TrainType id, so we just pick the first
    @Cacheable("trainTypesByName")
    @EntityGraph(attributePaths = { "trainCategory" })
    @Query("select t from TrainType t " +
           "where t.name = ?1 ")
    Optional<TrainType> findFirstByNameCached(final String name);
}
