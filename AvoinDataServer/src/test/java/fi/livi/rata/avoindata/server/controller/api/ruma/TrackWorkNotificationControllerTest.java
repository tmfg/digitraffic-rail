package fi.livi.rata.avoindata.server.controller.api.ruma;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrackWorkNotificationFactory;

public class TrackWorkNotificationControllerTest extends MockMvcBaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    @Autowired
    private TrackWorkNotificationRepository repository;

    private static final Random random = new Random(System.nanoTime());

    @BeforeEach
    public void setUp() {
        clearTwns();
    }

    @AfterEach
    public void tearDown() {
        clearTwns();
    }

    @Test
    public void all() throws Exception {
        final int amount = random.nextInt(10);
        IntStream.rangeClosed(1, amount).forEach(i -> factory.createPersist(1 + random.nextInt(10)));

        final ResultActions ra = getJson("/trackwork-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(amount)));
    }

    @Test
    public void all_after() throws Exception {
        final TrackWorkNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusDays(101);
        final TrackWorkNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra =
                getJson("/trackwork-notifications/status?start=" + ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void all_before() throws Exception {
        final TrackWorkNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(1);
        final TrackWorkNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson("/trackwork-notifications/status?end=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void all_between() throws Exception {
        final ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        final ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        final TrackWorkNotification before = factory.create(1).get(0);
        before.modified = start.minusMinutes(1);
        final TrackWorkNotification between = factory.create(1).get(0);
        between.modified = ZonedDateTime.now();
        final TrackWorkNotification after = factory.create(1).get(0);
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        final ResultActions ra = getJson(String.format("/trackwork-notifications/status?start=%s&end=%s",
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void versions() throws Exception {
        final List<TrackWorkNotification> twnVersions = factory.createPersist(1 + random.nextInt(10));
        final TrackWorkNotification twn = twnVersions.get(0);

        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s", twn.id.id));

        ra.andExpect(jsonPath("$.id").value(twn.id.id));
        for (final TrackWorkNotification v : twnVersions) {
            ra.andExpect(jsonPath(String.format("$.versions[%d].version", v.id.version - 1)).value(v.id.version));
        }
    }

    @Test
    public void versions_empty() throws Exception {
        final int twnId = random.nextInt(99999);
        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s", twnId));

        ra.andExpect(jsonPath("$.id").value(twnId));
        ra.andExpect(jsonPath("$.versions", empty()));
    }

    @Test
    public void singleVersion() throws Exception {
        final TrackWorkNotification twn = factory.createPersist(1).get(0);

        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s/%s", twn.id.id, twn.id.version));

        ra.andExpect(jsonPath("$[0]id").value(twn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(twn.id.version));
    }

    @Test
    public void singleVersion_empty() throws Exception {
        final int twnId = random.nextInt(99999);
        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s/%s", twnId, random.nextInt(99999)));

        ra.andExpect(jsonPath("$", empty()));
    }

    @Test
    public void latestVersion() throws Exception {
        final TrackWorkNotification twn = factory.createPersist(10).get(9);

        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s/latest.json", twn.id.id));

        ra.andExpect(jsonPath("$[0]id").value(twn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(twn.id.version));
    }

    @Test
    public void latestVersionGeoJson() throws Exception {
        final TrackWorkNotification twn = factory.createPersist(10).get(9);

        final ResultActions ra = getJson(String.format("/trackwork-notifications/%s/latest.geojson", twn.id.id));

        ra.andExpect(jsonPath("$.features", hasSize(1)));
        ra.andExpect(jsonPath("$.features[0].properties.id").value(twn.id.id));
        ra.andExpect(jsonPath("$.features[0].properties.version").value(twn.id.version));
    }

    @Test
    public void byState() throws Exception {
        final TrackWorkNotification twn = factory.create(1).get(0);
        twn.state = randomState();
        repository.save(twn);

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$[0]id").value(twn.id.id));
    }

    @Test
    public void byState_after() throws Exception {
        final TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(10);
        final TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(5);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&start=%s",
                TrackWorkNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void byState_before() throws Exception {
        final TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusDays(6);
        final TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().minusDays(3);
        repository.saveAll(Arrays.asList(before, after));

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&end=%s",
                TrackWorkNotificationState.DRAFT.name(),
                ZonedDateTime.now().minusDays(4).format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void byState_between() throws Exception {
        final ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        final ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        final TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = start.minusMinutes(1);
        final TrackWorkNotification between = factory.create(1).get(0);
        between.state = TrackWorkNotificationState.DRAFT;
        between.modified = ZonedDateTime.now();
        final TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&start=%s&end=%s",
                TrackWorkNotificationState.DRAFT,
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void byStateBetweenSameTime() throws Exception {
        final ZonedDateTime start = ZonedDateTime.now().minusHours(32);
        final ZonedDateTime end = start;

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&start=%s&end=%s",
                TrackWorkNotificationState.DRAFT,
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void geoJson() throws Exception {
        final TrackWorkNotification twn = factory.create(1).get(0);
        repository.save(twn);

        final ResultActions ra = getGeoJson(String.format("/trackwork-notifications.geojson?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$.features", hasSize(1)));
    }

    @Test
    public void trackWorkNotificationsWithRumaLocations() throws Exception {
        final TrackWorkNotification twn = factory.create(1).get(0);
        final RumaLocation rl = factory.createRumaLocation();
        final TrackWorkPart twp = factory.createWorkPart();
        twp.locations = Set.of(rl);
        twp.trackWorkNotification = twn;
        twn.trackWorkParts = Set.of(twp);

        repository.save(twn);

        final ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s", twn.state));
        ra.andExpect(jsonPath("$[0].id").value(twn.id.id));
        ra.andExpect(jsonPath("$[0].workParts[0].locations", hasSize(1)));
        ra.andExpect(jsonPath("$[0].workParts[0].locations[0].locationType").value(rl.locationType.name()));
    }

    @Transactional
    void clearTwns() {
        repository.deleteAllInBatch();
    }

    private TrackWorkNotificationState randomState() {
        final List<TrackWorkNotificationState> states = Arrays.asList(TrackWorkNotificationState.values());
        Collections.shuffle(states);
        return states.get(0);
    }

}
