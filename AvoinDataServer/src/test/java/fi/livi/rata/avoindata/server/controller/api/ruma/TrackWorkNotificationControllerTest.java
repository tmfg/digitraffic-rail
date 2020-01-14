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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
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
    public void all() throws Exception {
        int amount = random.nextInt(10);
        IntStream.rangeClosed(1, amount).forEach(i -> {
            factory.createPersist(1 + random.nextInt(10));
        });

        ResultActions ra = getJson("/trackwork-notifications");
        ra.andExpect(jsonPath("$", hasSize(amount)));
    }

    @Test
    public void all_after() throws Exception {
        TrackWorkNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(1);
        TrackWorkNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson("/trackwork-notifications/?start=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void all_before() throws Exception {
        TrackWorkNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(1);
        TrackWorkNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson("/trackwork-notifications/?end=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void all_between() throws Exception {
        ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        TrackWorkNotification before = factory.create(1).get(0);
        before.modified = start.minusMinutes(1);
        TrackWorkNotification between = factory.create(1).get(0);
        between.modified = ZonedDateTime.now();
        TrackWorkNotification after = factory.create(1).get(0);
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        ResultActions ra = getJson(String.format("/trackwork-notifications/?start=%s&end=%s",
                start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
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
