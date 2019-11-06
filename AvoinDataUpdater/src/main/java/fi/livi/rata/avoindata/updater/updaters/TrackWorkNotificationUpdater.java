package fi.livi.rata.avoindata.updater.updaters;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.service.TrackWorkNotificationService;

@Service
public class TrackWorkNotificationUpdater extends AEntityUpdater<TrackWorkNotification[]> {
    @Autowired
    private TrackWorkNotificationService trackWorkNotificationService;

    @Override
    @Scheduled(fixedDelay = 1000 * 30L)
    protected void update() {
        doUpdate("ruma/rti", trackWorkNotificationService::update, TrackWorkNotification[].class);
    }
}
