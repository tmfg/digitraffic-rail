package fi.livi.rata.avoindata.updater.updaters;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrafficRestrictionNotificationFactory;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrafficRestrictionNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrafficRestrictionNotificationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TrafficRestrictionNotificationUpdaterTest extends BaseTest {

    private TrafficRestrictionNotificationUpdater updater;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    private TrafficRestrictionNotificationRepository repository;
    @Autowired
    private LocalTrafficRestrictionNotificationService localTrafficRestrictionNotificationService;
    @Autowired
    private TrafficRestrictionNotificationFactory factory;
    @MockBean
    private RemoteTrafficRestrictionNotificationService remoteTrafficRestrictionNotificationService;
    @MockBean
    private LastUpdateService lastUpdateService;
    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Before
    public void setUp() {
        updater = new TrafficRestrictionNotificationUpdater(remoteTrafficRestrictionNotificationService, localTrafficRestrictionNotificationService, lastUpdateService,wgs84ConversionService, "http://fake-url");
    }

    @After
    public void tearDown() {
        testDataService.clearTrafficRestrictionNotifications();
    }

    @Test
    @Transactional
    public void addNew() {
        TrafficRestrictionNotification trn = factory.create(1).get(0);
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn.id.id, trn.id.version)});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(trn));

        updater.update();

        assertEquals(trn.id, repository.getOne(trn.id).id);
    }

    @Test
    @Transactional
    public void addNewMultipleVersions() {
        final List<TrafficRestrictionNotification> trns = factory.create(2);
        TrafficRestrictionNotification trn = trns.get(0);
        TrafficRestrictionNotification trn2 = trns.get(1);
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn2.id.id, trn2.id.version),});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(List.of(trn, trn2));

        updater.update();

        List<TrafficRestrictionNotification> savedTrns = repository.findAll();
        assertEquals(2, savedTrns.size());
        assertEquals(trn.id, savedTrns.get(0).id);
        assertEquals(trn2.id, savedTrns.get(1).id);
    }

    @Test
    @Transactional
    public void updateExistingForwards() {
        // only persist version 1
        List<TrafficRestrictionNotification> trnVersions = factory.create(2);
        TrafficRestrictionNotification trn = trnVersions.get(0);
        TrafficRestrictionNotification trnV2 = trnVersions.get(1);
        repository.save(trn);

        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn.id.id, trnV2.getVersion())});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(trnV2));

        updater.update();

        List<RumaNotificationIdAndVersion> idsAndVersions = repository.findIdsAndVersions(Collections.singleton(trn.id.id));
        assertEquals(2, idsAndVersions.size());
        assertEquals( trn.id.version.longValue(), idsAndVersions.get(0).getVersion().longValue());
        assertEquals( trnV2.id.version.longValue(), idsAndVersions.get(1).getVersion().longValue());
    }

    @Test
    @Transactional
    public void draftsAreNotPersisted() {
        TrafficRestrictionNotification trn = factory.create(1).get(0);
        trn.state = TrafficRestrictionNotificationState.DRAFT;
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn.id.id, trn.id.version)});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(trn));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void finishedWithPreviousDraftIsNotPersisted() {
        List<TrafficRestrictionNotification> trns = factory.create(2);
        TrafficRestrictionNotification draft = trns.get(0);
        TrafficRestrictionNotification finished = trns.get(1);
        draft.state = TrafficRestrictionNotificationState.DRAFT;
        finished.state = TrafficRestrictionNotificationState.FINISHED;
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(List.of(draft, finished));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void finishedWithPreviousSentIsPersisted() {
        List<TrafficRestrictionNotification> trns = factory.create(2);
        TrafficRestrictionNotification sent = trns.get(0);
        TrafficRestrictionNotification finished = trns.get(1);
        sent.state = TrafficRestrictionNotificationState.SENT;
        finished.state = TrafficRestrictionNotificationState.FINISHED;
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(List.of(sent, finished));

        updater.update();

        assertEquals(2, repository.findAll().size());
    }

    @Test
    @Transactional
    public void onlyPointIsPersistedForVaihdeMapGeometry() {
        TrafficRestrictionNotification trn = factory.create(1).get(0);
        RumaLocation loc = factory.createRumaLocation();
        IdentifierRange ir = factory.createIdentifierRange();
        ir.locationMap = geometryFactory.createGeometryCollection(new Geometry[]{
                geometryFactory.createLineString(new Coordinate[]{ TAMPERE_COORDINATE_TM35FIN, TAMPERE_COORDINATE_TM35FIN}),
                geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN)
        });
        ir.elementId = "1.2.246.586.1.24.123"; // vaihde
        trn.locations = Set.of(loc);
        loc.identifierRanges = Set.of(ir);
        ir.location = loc;
        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn.id.id, trn.id.version)});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(trn));

        updater.update();

        final TrafficRestrictionNotification savedTrn = repository.getOne(trn.id);
        final RumaLocation savedLoc = savedTrn.locations.iterator().next();
        final IdentifierRange savedIr = savedLoc.identifierRanges.iterator().next();
        assertEquals(Point.class, savedIr.locationMap.getClass());
    }

}
