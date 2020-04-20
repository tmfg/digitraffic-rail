package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.dao.RumaNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrackWorkNotificationFactory;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    @PersistenceContext
    private EntityManager entityManager;

    @Before
    public void setUp() {
        updater = new TrackWorkNotificationUpdater(remoteTrackWorkNotificationService, localTrackWorkNotificationService, lastUpdateService,wgs84ConversionService, "http://fake-url");
    }

    @After
    public void tearDown() {
        testDataService.clearTrackWorkNotifications();
    }

    @Test
    @Transactional
    public void addNew() {
        TrackWorkNotification twn = factory.create(1).get(0);
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();

        assertEquals(twn.id, repository.getOne(twn.id).id);
    }

    @Test
    @Transactional
    public void addNewMultipleVersions() {
        final List<TrackWorkNotification> twns = factory.create(2);
        final TrackWorkNotification twn = twns.get(0);
        final TrackWorkNotification twn2 = twns.get(1);
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn2.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(twn, twn2));

        updater.update();

        List<TrackWorkNotification> savedTwns = repository.findAll();
        assertEquals(2, savedTwns.size());
        assertEquals(twn.id, savedTwns.get(0).id);
        assertEquals(twn2.id, savedTwns.get(1).id);
    }

    @Test
    @Transactional
    public void updateExistingForwards() {
        // only persist version 1
        List<TrackWorkNotification> twnVersions = factory.create(2);
        TrackWorkNotification twn = twnVersions.get(0);
        TrackWorkNotification twnV2 = twnVersions.get(1);
        repository.save(twn);

        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twnV2.getVersion())});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twnV2));

        updater.update();

        List<RumaNotificationIdAndVersion> idsAndVersions = repository.findIdsAndVersions(Collections.singleton(twn.id.id));
        assertEquals(2, idsAndVersions.size());
        assertEquals( twn.id.version.longValue(), idsAndVersions.get(0).getVersion().longValue());
        assertEquals( twnV2.id.version.longValue(), idsAndVersions.get(1).getVersion().longValue());
    }

    @Test
    @Transactional
    public void coordinateReprojection() {
        TrackWorkNotification twn = factory.create(1).get(0);
        TrackWorkPart twp = factory.createTrackWorkPart();
        RumaLocation loc = factory.createRumaLocation();
        IdentifierRange ir = factory.createIdentifierRange();
        twn.trackWorkParts = Set.of(twp);
        twp.locations = Set.of(loc);
        twp.trackWorkNotification = twn;
        loc.identifierRanges = Set.of(ir);
        ir.location = loc;
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.getVersion())});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();
        TrackWorkNotification savedTwn = repository.getOne(twn.id);
        RumaLocation savedLoc = savedTwn.trackWorkParts.iterator().next().locations.iterator().next();
        IdentifierRange savedIr = savedLoc.identifierRanges.iterator().next();

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
        TrackWorkNotification twn = factory.create(1).get(0);
        twn.state = TrackWorkNotificationState.DRAFT;
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(twn.id.id, twn.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(Collections.singletonList(twn));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void paging() {
        List<TrackWorkNotification> twns = IntStream.rangeClosed(0, 999).mapToObj(i -> factory.create(1).get(0)).collect(Collectors.toList());
        List<TrackWorkNotification> twns2 = IntStream.rangeClosed(0, 499).mapToObj(i -> factory.create(1).get(0)).collect(Collectors.toList());
        List<TrackWorkNotification> bothtwns = new ArrayList<>();
        bothtwns.addAll(twns);
        bothtwns.addAll(twns2);
        RemoteRumaNotificationStatus[] twnstatuses = twns.stream().map(trn -> new RemoteRumaNotificationStatus(trn.id.id, trn.id.version)).toArray(RemoteRumaNotificationStatus[]::new);
        RemoteRumaNotificationStatus[] twnstatuses2 = twns2.stream().map(trn -> new RemoteRumaNotificationStatus(trn.id.id, trn.id.version)).toArray(RemoteRumaNotificationStatus[]::new);
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(twnstatuses);
        when(remoteTrackWorkNotificationService.getStatuses(1000)).thenReturn(twnstatuses2);
        final AtomicInteger invocation = new AtomicInteger(-1);
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return List.of(bothtwns.get(invocation.incrementAndGet()));
            }
        });

        updater.update();

        BigInteger count = (BigInteger) entityManager.createNativeQuery("SELECT count(*) FROM track_work_notification").getSingleResult();
        assertEquals(bothtwns.size(), count.intValue());
    }
    
    @Test
    @Transactional
    public void finishedWithPreviousDraftIsNotPersisted() {
        List<TrackWorkNotification> twns = factory.create(2);
        TrackWorkNotification draft = twns.get(0);
        TrackWorkNotification finished = twns.get(1);
        draft.state = TrackWorkNotificationState.DRAFT;
        finished.state = TrackWorkNotificationState.FINISHED;
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(draft, finished));

        updater.update();

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @Transactional
    public void finishedWithPreviousSentIsPersisted() {
        List<TrackWorkNotification> twns = factory.create(2);
        TrackWorkNotification sent = twns.get(0);
        TrackWorkNotification finished = twns.get(1);
        sent.state = TrackWorkNotificationState.SENT;
        finished.state = TrackWorkNotificationState.FINISHED;
        when(remoteTrackWorkNotificationService.getStatuses(0)).thenReturn(new RemoteRumaNotificationStatus[]{new RemoteRumaNotificationStatus(finished.id.id, finished.id.version)});
        when(remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(anyString(), any())).thenReturn(List.of(sent, finished));

        updater.update();

        assertEquals(2, repository.findAll().size());
    }
}
