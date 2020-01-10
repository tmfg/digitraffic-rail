package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrackWorkNotificationFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class TrackWorkNotificationControllerTest extends MockMvcBaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    @Autowired
    private TrackWorkNotificationRepository repository;

    private static final Random random = new Random(System.nanoTime());

    @Before
    public void setUp() {
        clearTwns();
    }

    @After
    public void tearDown() {
        clearTwns();
    }

    @Test
    public void all() throws Exception {
        int versionAmount = IntStream.rangeClosed(1, random.nextInt(10)).map(i -> {
            int versions = random.nextInt(10);
            factory.createPersist(versions);
            return versions;
        }).sum();

        assertLength("/trackwork-notifications", versionAmount);
    }

    @Test
    public void versions() throws Exception {
        List<TrackWorkNotification> twnVersions = factory.createPersist(random.nextInt(50));

        assertLength("/trackwork-notifications/" + twnVersions.get(0).id.id, twnVersions.size());
    }

    @Test
    public void singleVersion() throws Exception {
        TrackWorkNotification twn = factory.createPersist(1).get(0);

        ResultActions ra = getJson(String.format("/trackwork-notifications/%s/%s", twn.id.id, twn.id.version));
        ra.andExpect(jsonPath("$.id").value(twn.id.id));
        ra.andExpect(jsonPath("$.version").value(twn.id.version));
    }

    @Transactional
    void clearTwns() {
        repository.deleteAllInBatch();
    }

}
