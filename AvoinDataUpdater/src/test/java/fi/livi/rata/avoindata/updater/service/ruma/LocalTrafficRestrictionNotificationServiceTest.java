package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrafficRestrictionNotificationFactory;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class LocalTrafficRestrictionNotificationServiceTest extends BaseTest {

    @Autowired
    private TrafficRestrictionNotificationFactory factory;

    @Autowired
    private LocalTrafficRestrictionNotificationService service;

    private static final Random random = new Random(System.nanoTime());

    @After
    public void tearDown() {
        testDataService.clearTrafficRestrictionNotifications();
    }

    @Test
    public void getLocalTrafficRestrictionNotifications() {
        int twnCount = 1 + random.nextInt(10);
        IntStream.rangeClosed(0, twnCount).forEach(i -> {
            int maxVersion = 1 + random.nextInt(49);
            List<TrafficRestrictionNotification> twns = factory.createPersist(maxVersion);

            List<LocalRumaNotificationStatus> localTwns = service.getLocalTrafficRestrictionNotifications(Collections.singleton(twns.get(0).id.id));

            assertEquals(1, localTwns.size());
            LocalRumaNotificationStatus localTwn = localTwns.get(0);
            assertEquals(1, localTwn.minVersion);
            assertEquals(maxVersion, localTwn.maxVersion);
        });
    }

}
