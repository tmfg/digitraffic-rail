package fi.livi.rata.avoindata.updater.updaters;

import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TrafficRestrictionNotificationUpdaterTest extends BaseTest {

    private TrafficRestrictionNotificationUpdater updater;

    private GeometryFactory geometryFactory = new GeometryFactory();

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
    public void updateExistingBackwards() {
        // only persist version 2
        List<TrafficRestrictionNotification> trnVersions = factory.create(2);
        TrafficRestrictionNotification trn = trnVersions.get(0);
        TrafficRestrictionNotification trnV2 = trnVersions.get(1);
        repository.save(trnV2);

        when(remoteTrafficRestrictionNotificationService.getStatuses()).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(trn.id.id, trnV2.getVersion())});
        when(remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(trn));

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

}
