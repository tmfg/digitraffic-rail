package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StationUpdater extends AEntityUpdater<Station[]> {
    @Autowired
    private StationService stationService;

    //Every midnight 1:03
    @Override
    @Scheduled(cron = "0 1 3 * * ?")
    protected void update() {
        doUpdate("stations", stationService::update, Station[].class);
    }
}
