package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrackWorkNotificationFactory;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class LocalTrackWorkNotificationServiceTest extends BaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    @Autowired
    private LocalTrackWorkNotificationService service;

    private static final Random random = new Random(System.nanoTime());

    @After
    public void tearDown() {
        testDataService.clearTrackWorkNotifications();
    }

    @Test
    public void getLocalTrackWorkNotifications() {
        int twnCount = random.nextInt(10);
        IntStream.rangeClosed(0, twnCount).forEach(i -> {
            int maxVersion = random.nextInt(50);
            List<TrackWorkNotification> twns = factory.createPersist(maxVersion);

            List<LocalTrackWorkNotificationStatus> localTwns = service.getLocalTrackWorkNotifications(Collections.singleton(twns.get(0).id.id));

            assertEquals(1, localTwns.size());
            LocalTrackWorkNotificationStatus localTwn = localTwns.get(0);
            assertEquals(1, localTwn.minVersion);
            assertEquals(maxVersion, localTwn.maxVersion);
        });
    }

}
