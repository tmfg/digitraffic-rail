package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import fi.livi.rata.avoindata.updater.service.OperatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OperatorUpdater extends AEntityUpdater<Operator[]> {
    @Autowired
    private OperatorService operatorService;

    //Every midnight 1:11
    @Override
    @Scheduled(cron = "0 1 11 * * ?")
    protected void update() {
        doUpdate("operators", operatorService::update, Operator[].class);
    }
}
