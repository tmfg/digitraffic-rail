package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrafficRestrictionNotificationFactory;
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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class TrafficRestrictionControllerTest extends MockMvcBaseTest {

    @Autowired
    private TrafficRestrictionNotificationFactory factory;

    @Autowired
    private TrafficRestrictionNotificationRepository repository;

    private static final Random random = new Random(System.nanoTime());

    @Before
    public void setUp() {
        clearTrns();
    }

    @After
    public void tearDown() {
        clearTrns();
    }

    @Test
    public void all() throws Exception {
        int amount = random.nextInt(10);
        IntStream.rangeClosed(1, amount).forEach(i -> {
            factory.createPersist(1 + random.nextInt(10));
        });

        ResultActions ra = getJson("/trafficrestriction-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(amount)));
    }

    @Test
    public void typeOtherAreNotReturned() throws Exception {
        final List<TrafficRestrictionNotification> trn = factory.create(1);
        trn.get(0).limitation = TrafficRestrictionType.OTHER;
        repository.saveAll(trn);

        ResultActions ra = getJson("/trafficrestriction-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void all_after() throws Exception {
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusDays(10);
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson("/trafficrestriction-notifications/status?start=" + ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void all_before() throws Exception {
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(5);
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(5);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson("/trafficrestriction-notifications/status?end=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void all_between() throws Exception {
        ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = start.minusMinutes(1);
        TrafficRestrictionNotification between = factory.create(1).get(0);
        between.modified = ZonedDateTime.now();
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications/status?start=%s&end=%s",
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void versions() throws Exception {
        List<TrafficRestrictionNotification> trnVersions = factory.createPersist(1 + random.nextInt(10));
        TrafficRestrictionNotification trn = trnVersions.get(0);

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s", trn.id.id));

        ra.andExpect(jsonPath("$.id").value(trn.id.id));
        for (TrafficRestrictionNotification v : trnVersions) {
            ra.andExpect(jsonPath(String.format("$.versions[%d].version", v.id.version - 1)).value(v.id.version));
        }
    }

    @Test
    public void versions_empty() throws Exception {
        int trnId = random.nextInt(99999);
        ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s", trnId));

        ra.andExpect(jsonPath("$.id").value(trnId));
        ra.andExpect(jsonPath("$.versions", empty()));
    }

    @Test
    public void singleVersion() throws Exception {
        TrafficRestrictionNotification trn = factory.createPersist(1).get(0);

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/%s", trn.id.id, trn.id.version));

        ra.andExpect(jsonPath("$[0]id").value(trn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(trn.id.version));
    }

    @Test
    public void singleVersion_empty() throws Exception {
        int trnId = random.nextInt(99999);
        ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/%s", trnId, random.nextInt(99999)));

        ra.andExpect(jsonPath("$", empty()));
    }

    @Test
    public void byState() throws Exception {
        TrafficRestrictionNotification trn = factory.create(1).get(0);
        repository.save(trn);

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s", trn.state.name()));

        ra.andExpect(jsonPath("$[0]id").value(trn.id.id));
    }

    @Test
    public void byState_after() throws Exception {
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(10);
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&start=%s",
                TrafficRestrictionNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void byState_before() throws Exception {
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(6);
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(3);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&end=%s",
                TrafficRestrictionNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(4).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void byState_between() throws Exception {
        ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.SENT;
        before.modified = start.minusMinutes(1);
        TrafficRestrictionNotification between = factory.create(1).get(0);
        between.state = TrafficRestrictionNotificationState.SENT;
        between.modified = ZonedDateTime.now();
        TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.SENT;
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&start=%s&end=%s",
                TrafficRestrictionNotificationState.SENT,
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void geoJson() throws Exception {
        TrafficRestrictionNotification trn = factory.create(1).get(0);
        repository.save(trn);

        ResultActions ra = getGeoJson(String.format("/trafficrestriction-notifications.geojson?state=%s", trn.state.name()));

        ra.andExpect(jsonPath("$.features", hasSize(1)));
    }

    @Transactional
    void clearTrns() {
        repository.deleteAllInBatch();
    }

}
