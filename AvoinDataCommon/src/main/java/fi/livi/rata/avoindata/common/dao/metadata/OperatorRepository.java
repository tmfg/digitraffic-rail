package fi.livi.rata.avoindata.common.dao.metadata;

import java.util.List;

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

    Operator findByOperatorShortCode(String operatorShortCode);
}
