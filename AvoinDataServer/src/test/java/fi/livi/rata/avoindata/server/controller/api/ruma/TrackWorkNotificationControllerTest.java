package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class TrackWorkNotificationControllerTest extends MockMvcBaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    @Autowired
    private TrackWorkNotificationRepository repository;

    private static final Random random = new Random(System.nanoTime());

    private final GeometryFactory geometryFactory = new GeometryFactory();

    private final Point POINT_MAP = geometryFactory.createPoint(new Coordinate(328500.3, 6822410));
    private final Point POINT_SCHEMA = geometryFactory.createPoint(new Coordinate(328509, 6822419));

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

        ResultActions ra = getJson("/trackwork-notifications/status");
        ra.andExpect(jsonPath("$", hasSize(amount)));
    }

    @Test
    public void all_after() throws Exception {
        TrackWorkNotification before = factory.create(1).get(0);
        before.modified = ZonedDateTime.now().minusMinutes(1);
        TrackWorkNotification after = factory.create(1).get(0);
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson("/trackwork-notifications/status?start=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
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

        ResultActions ra = getJson("/trackwork-notifications/status?end=" + ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
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

        ResultActions ra = getJson(String.format("/trackwork-notifications/status?start=%s&end=%s",
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
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
    public void versions_empty() throws Exception {
        int twnId = random.nextInt(99999);
        ResultActions ra = getJson(String.format("/trackwork-notifications/%s", twnId));

        ra.andExpect(jsonPath("$.id").value(twnId));
        ra.andExpect(jsonPath("$.versions", empty()));
    }

    @Test
    public void singleVersion() throws Exception {
        TrackWorkNotification twn = factory.createPersist(1).get(0);

        ResultActions ra = getJson(String.format("/trackwork-notifications/%s/%s", twn.id.id, twn.id.version));

        ra.andExpect(jsonPath("$[0]id").value(twn.id.id));
        ra.andExpect(jsonPath("$[0]version").value(twn.id.version));
    }

    @Test
    public void singleVersion_empty() throws Exception {
        int twnId = random.nextInt(99999);
        ResultActions ra = getJson(String.format("/trackwork-notifications/%s/%s", twnId, random.nextInt(99999)));

        ra.andExpect(jsonPath("$", empty()));
    }

    @Test
    public void byState() throws Exception {
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.state = randomState();
        repository.save(twn);

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$[0]id").value(twn.id.id));
    }

    @Test
    public void byState_after() throws Exception {
        TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusMinutes(1);
        TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&start=%s",
                TrackWorkNotificationState.DRAFT.name(),
                ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(after.id.id));
    }

    @Test
    public void byState_before() throws Exception {
        TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = ZonedDateTime.now().minusMinutes(1);
        TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = ZonedDateTime.now().plusMinutes(1);
        repository.saveAll(Arrays.asList(before, after));

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&end=%s",
                TrackWorkNotificationState.DRAFT.name(),
                ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(before.id.id));
    }

    @Test
    public void byState_between() throws Exception {
        ZonedDateTime start = ZonedDateTime.now().minusHours(1);
        ZonedDateTime end = ZonedDateTime.now().plusHours(1);
        TrackWorkNotification before = factory.create(1).get(0);
        before.state = TrackWorkNotificationState.DRAFT;
        before.modified = start.minusMinutes(1);
        TrackWorkNotification between = factory.create(1).get(0);
        between.state = TrackWorkNotificationState.DRAFT;
        between.modified = ZonedDateTime.now();
        TrackWorkNotification after = factory.create(1).get(0);
        after.state = TrackWorkNotificationState.DRAFT;
        after.modified = end.plusMinutes(1);
        repository.saveAll(Arrays.asList(before, between, after));

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s&start=%s&end=%s",
                TrackWorkNotificationState.DRAFT,
                start.format(DateTimeFormatter.ISO_DATE_TIME),
                end.format(DateTimeFormatter.ISO_DATE_TIME)));
        ra.andExpect(jsonPath("$", hasSize(1)));
        ra.andExpect(jsonPath("$[0].id").value(between.id.id));
    }

    @Test
    public void byState_map() throws Exception {
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.locationMap = POINT_MAP;
        twn.locationSchema = POINT_SCHEMA;
        repository.save(twn);

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$[0].location[0]").value(POINT_MAP.getX()));
        ra.andExpect(jsonPath("$[0].location[1]").value(POINT_MAP.getY()));
    }

    @Test
    public void byState_schema() throws Exception {
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.locationMap = POINT_MAP;
        twn.locationSchema = POINT_SCHEMA;
        repository.save(twn);

        ResultActions ra = getJson(String.format("/trackwork-notifications.json?schema=true&state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$[0].location[0]").value(POINT_SCHEMA.getX()));
        ra.andExpect(jsonPath("$[0].location[1]").value(POINT_SCHEMA.getY()));
    }

    @Test
    public void geoJson_map() throws Exception {
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.locationMap = POINT_MAP;
        twn.locationSchema = POINT_SCHEMA;
        repository.save(twn);

        ResultActions ra = getGeoJson(String.format("/trackwork-notifications.geojson?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(POINT_MAP.getX()));
        ra.andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(POINT_MAP.getY()));
    }

    @Test
    public void geoJson_schema() throws Exception {
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.locationMap = POINT_MAP;
        twn.locationSchema = POINT_SCHEMA;
        repository.save(twn);

        ResultActions ra = getGeoJson(String.format("/trackwork-notifications.geojson?state=%s", twn.state.name()));

        ra.andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(POINT_SCHEMA.getX()));
        ra.andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(POINT_MAP.getY()));
    }

    @Transactional
    void clearTwns() {
        repository.deleteAllInBatch();
    }

    private TrackWorkNotificationState randomState() {
        List<TrackWorkNotificationState> states = Arrays.asList(TrackWorkNotificationState.values());
        Collections.shuffle(states);
        return states.get(0);
    }
}
