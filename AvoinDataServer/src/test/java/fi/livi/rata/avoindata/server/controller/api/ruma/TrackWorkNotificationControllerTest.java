package fi.livi.rata.avoindata.server.controller.api.ruma;

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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
    public void versions() throws Exception {
        List<TrackWorkNotification> twnVersions = factory.createPersist(1 + random.nextInt(10));
        TrackWorkNotification twn = twnVersions.get(0);

        ResultActions ra = getJson(String.format("/trackwork-notifications/%s", twn.id.id));

        ra.andExpect(jsonPath("$.id").value(twn.id.id));
        for (TrackWorkNotification v : twnVersions) {
            ra.andExpect(jsonPath(String.format("$.versions[%d].version", v.id.version - 1)).value(v.id.version));
        }
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
