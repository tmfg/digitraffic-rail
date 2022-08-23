package fi.livi.rata.avoindata.updater.service.ruma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrackWorkNotificationFactory;

public class LocalTrackWorkNotificationServiceTest extends BaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    @Autowired
    private LocalTrackWorkNotificationService service;

    private static final Random random = new Random(System.nanoTime());

    @AfterEach
    public void tearDown() {
        testDataService.clearTrackWorkNotifications();
    }

    @Test
    public void getLocalTrackWorkNotifications() {
        int twnCount = 1 + random.nextInt(10);
        IntStream.rangeClosed(0, twnCount).forEach(i -> {
            int maxVersion = 1 + random.nextInt(49);
            List<TrackWorkNotification> twns = factory.createPersist(maxVersion);

            List<LocalRumaNotificationStatus> localTwns = service.getLocalTrackWorkNotifications(Collections.singleton(twns.get(0).id.id));

            assertEquals(1, localTwns.size());
            LocalRumaNotificationStatus localTwn = localTwns.get(0);
            assertEquals(1, localTwn.minVersion);
            assertEquals(maxVersion, localTwn.maxVersion);
        });
    }

}
