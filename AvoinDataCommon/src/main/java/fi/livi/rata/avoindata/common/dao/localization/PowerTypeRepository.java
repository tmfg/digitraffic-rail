package fi.livi.rata.avoindata.common.dao.localization;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.localization.PowerType;

@Repository
public interface PowerTypeRepository extends CustomGeneralRepository<PowerType, Long> {

    @Cacheable("powerTypes")
    @Query("select t from PowerType t where t.abbreviation = ?1")
    Optional<PowerType> findByAbbreviationCached(String abbreviation);
}
