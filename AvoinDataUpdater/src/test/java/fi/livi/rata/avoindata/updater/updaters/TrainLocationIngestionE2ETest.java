package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainExistenceCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end ingestion test for the scheduled {@link TrainLocationUpdater#trainLocation()} orchestrator:
 * mocked PALA HTTP fetch -> real {@link fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer}
 * -> real filters -> DB persist, asserted via the repository the REST controller uses.
 *
 * <p>Only the PALA HTTP fetch is mocked ({@link RipaService#getFromPalaAsString(String)}); train existence is backed
 * by a real persisted train row, so the unknown-train filter is exercised against the production repository path.
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
    private TrainRepository trainRepository;

    @Autowired
    private TrainLocationUpdater trainLocationUpdater;

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Autowired
    private TrainExistenceCache trainExistenceCache;

    private String loadFixture(final String name) throws IOException {
        return new String(new ClassPathResource(name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void setupTrains() {
        trainRepository.save(new Train(9001L, DEPARTURE_DATE, 1, "test", 1L, 1L, "Z", true, false, 1L,
                Train.TimetableType.REGULAR, ZonedDateTime.now()));

        trainRepository.save(new Train(9002L, DEPARTURE_DATE, 1, "test", 1L, 1L, "Z", true, false, 1L,
                Train.TimetableType.REGULAR, ZonedDateTime.now()));
    }

    @AfterEach
    public void cleanUp() {
        trainExistenceCache.clear();
    }

    @Test
    public void palaResponseShouldPersistKnownTrain() throws Exception {
        when(ripaService.getFromPalaAsString(anyString())).thenReturn(loadFixture("pala-ingestion-e2e.json"));

        setupTrains();
        trainLocationUpdater.trainLocation();

        final List<TrainLocation> locationsFor9001 = trainLocationRepository.findForTrain(9001L, DEPARTURE_DATE);
        Assertions.assertEquals(1, locationsFor9001.size(), "on-track train should be persisted");
        Assertions.assertEquals(50, locationsFor9001.getFirst().speed.intValue());
        Assertions.assertEquals(11, locationsFor9001.getFirst().accuracy.intValue());
        Assertions.assertTrue(locationsFor9001.getFirst().isGpsLocation, "GPS-based position should have isGpsLocation=true");

        final List<TrainLocation> locationsFor9002 = trainLocationRepository.findForTrain(9002L, DEPARTURE_DATE);
        Assertions.assertEquals(0, locationsFor9002.size(), "off-track train should not be persisted");
    }

    @Test
    public void unknownTrainLocationShouldBeFilteredOut() throws Exception {
        when(ripaService.getFromPalaAsString(anyString())).thenReturn(loadFixture("pala-ingestion-e2e.json"));

        // no trains inserted
        trainLocationUpdater.trainLocation();

        Assertions.assertEquals(0, trainLocationRepository.findForTrain(9001L, DEPARTURE_DATE).size(),
                "unknown train location should be dropped by the train-existence filter");
    }
}
