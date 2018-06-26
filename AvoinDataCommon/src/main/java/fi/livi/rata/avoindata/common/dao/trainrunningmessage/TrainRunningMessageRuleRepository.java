package fi.livi.rata.avoindata.common.dao.trainrunningmessage;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;

@Repository
public interface TrainRunningMessageRuleRepository extends CustomGeneralRepository<TrainRunningMessageRule, Long> {

}
