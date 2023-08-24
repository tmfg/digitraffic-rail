package fi.livi.rata.avoindata.server.controller.api.ruma;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrafficRestrictionNotificationFactory;

public class TrafficRestrictionControllerTest extends MockMvcBaseTest {

    @Autowired
    private TrafficRestrictionNotificationFactory factory;

    @Autowired
    private TrafficRestrictionNotificationRepository repository;

    private static final Random random = new Random(System.nanoTime());

    @BeforeEach
    public void setUp() {
        clearTrns();
    }

    @AfterEach
    public void tearDown() {
        clearTrns();
    }

    @Test
    public void all() throws Exception {
        final int amount = random.nextInt(10);
        IntStream.rangeClosed(1, amount).forEach(i -> factory.createPersist(1 + random.nextInt(10)));

        final ResultActions ra = getJson("/trafficrestriction-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(amount)));
    }

    @Test
    public void typeOtherAreNotReturned() throws Exception {
        final List<TrafficRestrictionNotification> trn = factory.create(1);
        trn.get(0).limitation = TrafficRestrictionType.OTHER;
        repository.saveAll(trn);

        final ResultActions ra = getJson("/trafficrestriction-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void all_after() throws Exception {
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusDays(10);
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson("/trafficrestriction-notifications/status?start=" + ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void all_before() throws Exception {
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(5);
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(5);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson("/trafficrestriction-notifications/status?end=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void all_between() throws Exception {
        final ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        final ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.modified = start.minusMinutes(1);
        final TrafficRestrictionNotification between = factory.create(1).get(0);
        between.modified = ZonedDateTime.now();
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/status?start=%s&end=%s",
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void versions() throws Exception {
        final List<TrafficRestrictionNotification> trnVersions = factory.createPersist(1 + random.nextInt(10));
        final TrafficRestrictionNotification trn = trnVersions.get(0);

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s", trn.id.id));

        ra.andExpect(jsonPath("$.id").value(trn.id.id));
        for (TrafficRestrictionNotification v : trnVersions) {
            ra.andExpect(jsonPath(String.format("$.versions[%d].version", v.id.version - 1)).value(v.id.version));
        }
    }

    @Test
    public void versions_empty() throws Exception {
        final int trnId = random.nextInt(99999);
        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s", trnId));

        ra.andExpect(jsonPath("$.id").value(trnId));
        ra.andExpect(jsonPath("$.versions", empty()));
    }

    @Test
    public void singleVersion() throws Exception {
        final TrafficRestrictionNotification trn = factory.createPersist(1).get(0);

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/%s", trn.id.id, trn.id.version));

        ra.andExpect(jsonPath("$[0]id").value(trn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(trn.id.version));
    }

    @Test
    public void singleVersion_empty() throws Exception {
        final int trnId = random.nextInt(99999);
        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/%s", trnId, random.nextInt(99999)));

        ra.andExpect(jsonPath("$", empty()));
    }

    @Test
    public void latestVersion() throws Exception {
        final TrafficRestrictionNotification trn = factory.createPersist(10).get(9);

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/latest.json", trn.id.id));

        ra.andExpect(jsonPath("$[0]id").value(trn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(trn.id.version));
    }

    @Test
    public void latestVersionGeoJson() throws Exception {
        final TrafficRestrictionNotification trn = factory.createPersist(10).get(9);

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications/%s/latest.geojson", trn.id.id));

        ra.andExpect(jsonPath("$.features", hasSize(1)));
        ra.andExpect(jsonPath("$.features[0].properties.id").value(trn.id.id));
        ra.andExpect(jsonPath("$.features[0].properties.version").value(trn.id.version));
    }

    @Test
    public void byState() throws Exception {
        final TrafficRestrictionNotification trn = factory.create(1).get(0);
        repository.save(trn);

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s", trn.state.name()));

        ra.andExpect(jsonPath("$[0]id").value(trn.id.id));
    }

    @Test
    public void byState_after() throws Exception {
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(10);
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&start=%s",
                TrafficRestrictionNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void byState_before() throws Exception {
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(6);
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(3);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&end=%s",
                TrafficRestrictionNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(4).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void byState_between() throws Exception {
        final ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        final ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        final TrafficRestrictionNotification before = factory.create(1).get(0);
        before.state = TrafficRestrictionNotificationState.SENT;
        before.modified = start.minusMinutes(1);
        final TrafficRestrictionNotification between = factory.create(1).get(0);
        between.state = TrafficRestrictionNotificationState.SENT;
        between.modified = ZonedDateTime.now();
        final TrafficRestrictionNotification after = factory.create(1).get(0);
        after.state = TrafficRestrictionNotificationState.SENT;
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        final ResultActions ra = getJson(String.format("/trafficrestriction-notifications.json?state=%s&start=%s&end=%s",
                TrafficRestrictionNotificationState.SENT,
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void geoJson() throws Exception {
        final TrafficRestrictionNotification trn = factory.create(1).get(0);
        repository.save(trn);

        final ResultActions ra = getGeoJson(String.format("/trafficrestriction-notifications.geojson?state=%s", trn.state.name()));

        ra.andExpect(jsonPath("$.features", hasSize(1)));
    }

    @Transactional
    void clearTrns() {
        repository.deleteAllInBatch();
    }

}
