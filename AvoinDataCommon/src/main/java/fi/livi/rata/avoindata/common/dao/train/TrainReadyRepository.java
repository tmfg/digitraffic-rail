package fi.livi.rata.avoindata.common.dao.train;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.train.TrainReady;

@Repository
public interface TrainReadyRepository extends CustomGeneralRepository<TrainReady, Long> {

}
