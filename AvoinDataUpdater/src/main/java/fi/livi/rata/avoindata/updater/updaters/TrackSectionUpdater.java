package fi.livi.rata.avoindata.updater.updaters;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;
import fi.livi.rata.avoindata.updater.service.TrackSectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@XRayEnabled
public class TrackSectionUpdater extends AEntityUpdater<TrackSection[]> {
    @Autowired
    private TrackSectionService trackSectionService;

    //Every midnight 1:01
    @Override
    @Scheduled(cron = "0 1 1 * * ?")
    protected void update() {
        doUpdate("tracksections", trackSectionService::updateTrackSections, TrackSection[].class);
    }
}
