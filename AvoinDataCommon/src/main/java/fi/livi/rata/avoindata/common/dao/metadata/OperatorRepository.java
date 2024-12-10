package fi.livi.rata.avoindata.common.dao.metadata;

import java.util.Collection;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;

@Repository
public interface OperatorRepository extends CustomGeneralRepository<Operator, Integer> {

    @Query("select distinct operator from Operator operator " +
            "left join fetch operator.trainNumbers " +
            "order by operator.operatorName")
    List<Operator> findAllAndFetchTrainNumbers();

    Operator findByOperatorShortCode(final String operatorShortCode);

    @Cacheable("operators")
    @Query("select operator from Operator operator where operator.operatorUICCode = ?1")
    Operator findByOperatorUICCodeCached(final int operatorUICCode);

    @Override
    @CacheEvict(cacheNames = "operators", allEntries = true)
    void persist(final Collection<Operator> objects);

}
