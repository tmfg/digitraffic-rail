package fi.livi.rata.avoindata.updater.updaters;

import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrackWorkNotificationFactory;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationService;

public class TrackWorkNotificationUpdaterTest extends BaseTest {

    private TrackWorkNotificationUpdater updater;

    @Autowired
    private TrackWorkNotificationRepository repository;
    @Autowired
    private LocalTrackWorkNotificationService localTrackWorkNotificationService;
    @Autowired
    private TrackWorkNotificationFactory factory;
    @MockBean
    private RemoteTrackWorkNotificationService remoteTrackWorkNotificationService;
    @MockBean
    private LastUpdateService lastUpdateService;
    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @BeforeEach
    public void setUp() {
        updater = new TrackWorkNotificationUpdater(remoteTrackWorkNotificationService, localTrackWorkNotificationService, lastUpdateService,wgs84ConversionService, "http://fake-url", "");
    }

    @AfterEach
    public void tearDown() {
        testDataService.clearTrackWorkNotifications();
    }

    @Test
    @Transactional
    public void addNew() {
        final TrackWorkNotification twn = factory.create(1).get(0);
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();

        assertEquals(twn.id, repository.getReferenceById(twn.id).id);
    }

    @Test
    @Transactional
    public void addNewMultipleVersions() {
        final List<TrackWorkNotification> twns = factory.create(2);
        final TrackWorkNotification twn = twns.get(0);
        final TrackWorkNotification twn2 = twns.get(1);
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn2.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(twn, twn2));

        updater.update();

        final List<TrackWorkNotification> savedTwns = repository.findAll();
        assertEquals(2, savedTwns.size());
        assertEquals(twn.id, savedTwns.get(0).id);
        assertEquals(twn2.id, savedTwns.get(1).id);
    }

    @Test
    @Transactional
    public void updateExistingForwards() {
        // only persist version 1
        final List<TrackWorkNotification> twnVersions = factory.create(2);
        final TrackWorkNotification twn = twnVersions.get(0);
        final TrackWorkNotification twnV2 = twnVersions.get(1);
        repository.save(twn);

        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twnV2.getVersion())});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twnV2));

        updater.update();

        final List<RumaNotificationIdAndVersion> idsAndVersions = repository.findIdsAndVersions(Collections.singleton(twn.id.id));
        assertEquals(2, idsAndVersions.size());
        assertEquals( twn.id.version.longValue(), idsAndVersions.get(0).getVersion().longValue());
        assertEquals( twnV2.id.version.longValue(), idsAndVersions.get(1).getVersion().longValue());
    }

    @Test
    @Transactional
    public void coordinateReprojection() {
        final TrackWorkNotification twn = factory.create(1).get(0);
        final TrackWorkPart twp = factory.createTrackWorkPart();
        final RumaLocation loc = factory.createRumaLocation();
        final IdentifierRange ir = factory.createIdentifierRange();
        twn.trackWorkParts = Set.of(twp);
        twp.locations = Set.of(loc);
        twp.trackWorkNotification = twn;
        loc.identifierRanges = Set.of(ir);
        ir.location = loc;
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.getVersion())});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();
        final TrackWorkNotification savedTwn = repository.getReferenceById(twn.id);
        final RumaLocation savedLoc = savedTwn.trackWorkParts.iterator().next().locations.iterator().next();
        final IdentifierRange savedIr = savedLoc.identifierRanges.iterator().next();

        assertEquals(twn.locationMap, savedTwn.locationMap);
        assertEquals(twn.locationSchema, savedTwn.locationSchema);
        assertEquals(loc.locationMap, savedLoc.locationMap);
        assertEquals(loc.locationSchema, savedLoc.locationSchema);
        assertEquals(ir.locationMap, savedIr.locationMap);
        assertEquals(ir.locationSchema, savedIr.locationSchema);
    }

    @Test
    @Transactional
    public void draftsAreNotPersisted() {
        final TrackWorkNotification twn = factory.create(1).get(0);
        twn.state = TrackWorkNotificationState.DRAFT;
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void finishedWithPreviousDraftIsNotPersisted() {
        final List<TrackWorkNotification> twns = factory.create(2);
        final TrackWorkNotification draft = twns.get(0);
        final TrackWorkNotification finished = twns.get(1);
        draft.state = TrackWorkNotificationState.DRAFT;
        finished.state = TrackWorkNotificationState.FINISHED;
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(draft, finished));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void finishedWithPreviousSentIsPersisted() {
        final List<TrackWorkNotification> twns = factory.create(2);
        final TrackWorkNotification sent = twns.get(0);
        final TrackWorkNotification finished = twns.get(1);
        sent.state = TrackWorkNotificationState.SENT;
        finished.state = TrackWorkNotificationState.FINISHED;
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(sent, finished));

        updater.update();

        assertEquals(2, repository.findAll().size());
    }

    @Test
    @Transactional
    public void onlyPointIsPersistedForVaihdeMapGeometry() {
        final TrackWorkNotification twn = factory.create(1).get(0);
        final TrackWorkPart twp = factory.createTrackWorkPart();
        final RumaLocation loc = factory.createRumaLocation();
        final IdentifierRange ir = factory.createIdentifierRange();
        ir.locationMap = geometryFactory.createGeometryCollection(new Geometry[]{
            geometryFactory.createLineString(new Coordinate[]{ TAMPERE_COORDINATE_TM35FIN, TAMPERE_COORDINATE_TM35FIN}),
            geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN)
        });
        ir.elementId = "1.2.246.586.1.24.123"; // vaihde
        twn.trackWorkParts = Set.of(twp);
        twp.locations = Set.of(loc);
        twp.trackWorkNotification = twn;
        loc.identifierRanges = Set.of(ir);
        ir.location = loc;
        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.getVersion())});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();

        final TrackWorkNotification savedTwn = repository.getReferenceById(twn.id);
        final RumaLocation savedLoc = savedTwn.trackWorkParts.iterator().next().locations.iterator().next();
        final IdentifierRange savedIr = savedLoc.identifierRanges.iterator().next();
        assertEquals(Point.class, savedIr.locationMap.getClass());
    }

    @Test
    @Transactional
    public void ignore() {
        final TrackWorkNotification twn1 = factory.create(1).get(0);
        final TrackWorkNotification twn2 = factory.create(1).get(0);

        // set ignore manually
        updater = new TrackWorkNotificationUpdater(remoteTrackWorkNotificationService,
                localTrackWorkNotificationService,
                lastUpdateService,wgs84ConversionService,
                "http://fake-url",
                twn1.id.id + "," + twn2.id.id);

        when(remoteTrackWorkNotificationService.getStatuses()).thenReturn(
                new RemoteRumaNotificationStatus[]{
                        new RemoteRumaNotificationStatus(twn1.id.id, twn1.id.version),
                        new RemoteRumaNotificationStatus(twn2.id.id, twn2.id.version)});

        updater.update();

        assertEquals(0, repository.findAll().size());
    }
}
