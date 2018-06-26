package fi.livi.rata.avoindata.common.dao.metadata;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;

@Repository
public interface OperatorTrainNumberRepository extends CustomGeneralRepository<OperatorTrainNumber, Long> {
}