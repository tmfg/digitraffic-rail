package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.updater.service.TrackWorkNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class TrackWorkNotificationUpdater {

    @Autowired
    private TrackWorkNotificationService trackWorkNotificationService;

    @PostConstruct
    private void init() {
        new SimpleAsyncTaskExecutor().execute(this::update);
    }

    //@Scheduled(fixedDelay = 100000 * 30L)
    protected void update() {
        trackWorkNotificationService.update();
    }

}
