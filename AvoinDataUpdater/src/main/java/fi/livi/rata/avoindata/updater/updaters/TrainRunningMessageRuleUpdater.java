package fi.livi.rata.avoindata.updater.updaters;

import com.amazonaws.xray.AWSXRay;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import fi.livi.rata.avoindata.updater.service.TrainRunningMessageRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TrainRunningMessageRuleUpdater extends AEntityUpdater<TrainRunningMessageRule[]> {
    @Autowired
    private TrainRunningMessageRuleService timeTableRowActivationService;

    //Every midnight 2:02
    @Override
    @Scheduled(cron = "0 2 2 * * ?")
    protected void update() {
        AWSXRay.createSegment(this.getClass().getSimpleName(), (subsegment) -> {
            doUpdate("train-running-message-rules", timeTableRowActivationService::update, TrainRunningMessageRule[]
                    .class);
        });
    }
}
