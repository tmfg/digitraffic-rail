package fi.livi.rata.avoindata.updater.updaters;

import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.RipaService;

/**
 * End-to-end ingestion test: RIPA JSON fixture -> real {@link fi.livi.rata.avoindata.updater.deserializers.TrainLocationDeserializer}
 * -> real filters -> DB persist, asserted via the repository read the REST controller uses.
 *
 * <p>Only the RIPA HTTP fetch is mocked ({@link RipaService}); its return value is produced by the real mapper via
 * {@code testDataService.parseEntityList(...)}, mirroring how production creates {@link TrainLocation} objects.
 *
 * <p>The {@code updater.liikeinterface-url} guard is enabled by reflection on the updater bean only — setting it as a
 * global property would activate every RIPA initializer's {@code @PostConstruct} (which require per-prefix numeric
 * config absent from the test properties) and fail context startup.
 *
 * <p>Requires the same infrastructure as {@code TrainLocationNearTrackFilterServiceTest} (track boundary data) plus the
 * shared MySQL. MQTT delivery is disabled (mqtt.enable=false); the updater's own try/catch swallows MQTT failures.
 */
@Transactional
@TestPropertySource(properties = { "mqtt.enable=false" })
public class TrainLocationIngestionE2ETest extends BaseTest {
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 1, 1);

    @MockitoBean
    private RipaService ripaService;

    @Autowired
    private TrainLocationUpdater trainLocationUpdater;

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Test
    public void ripaFixtureShouldDeserializeFilterAndPersist() throws Exception {
        // Fixture: on-track train 9001 + an identical duplicate of it + off-track train 9002.
        final List<TrainLocation> fixture = testDataService.parseEntityList("trainlocation/ingestion-e2e.json", TrainLocation[].class);
        when(ripaService.getFromRipa("kuplas", TrainLocation[].class)).thenReturn(fixture.toArray(new TrainLocation[0]));

        // Enable the trainLocation() guard on just this bean (updater.liikeinterface-url must be non-empty).
        final TrainLocationUpdater target = AopTestUtils.getUltimateTargetObject(trainLocationUpdater);
        ReflectionTestUtils.setField(target, "liikeinterfaceUrl", "http://mock-ripa");
        ReflectionTestUtils.setField(target, "isKuplaEnabled", true);
        ReflectionTestUtils.setField(target, "isPalaEnabled", false);

        trainLocationUpdater.trainLocation();

        // On-track train 9001 survives; its duplicate is deduped away by RecentlySeenTrainLocationFilter.
        final List<TrainLocation> survivors = trainLocationRepository.findTrain(9001L, DEPARTURE_DATE);
        Assertions.assertEquals(1, survivors.size(), "duplicate should be deduped to a single row");
        Assertions.assertEquals(50, survivors.getFirst().speed.intValue());
        Assertions.assertEquals(11, survivors.getFirst().accuracy.intValue());

        // Off-track train 9002 is dropped by the near-track filter.
        Assertions.assertEquals(0, trainLocationRepository.findTrain(9002L, DEPARTURE_DATE).size(),
                "off-track location should be dropped by the near-track filter");
    }
}
