package fi.livi.rata.avoindata.updater.updaters;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.RipaService;

/**
 * End-to-end ingestion test for the scheduled {@link TrainLocationUpdater#trainLocation()} orchestrator:
 * mocked PALA HTTP fetch -> real {@link fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer}
 * -> real filters -> DB persist, asserted via the repository the REST controller uses.
 *
 * <p>Only the PALA HTTP fetch is mocked ({@link RipaService#getFromPalaAsString(String)}); everything downstream is
 * the real production pipeline.
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

    private String loadFixture(final String name) throws IOException {
        return new String(new ClassPathResource(name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    public void palaResponseShouldDeserializeFilterAndPersist() throws Exception {
        // Fixture: on-track GPS train 9001 + off-track train 9002.
        when(ripaService.getFromPalaAsString(anyString())).thenReturn(loadFixture("pala-ingestion-e2e.json"));

        trainLocationUpdater.trainLocation();

        // On-track train 9001 is persisted with its mapped fields (tarkkuus 11000 mm -> accuracy 11 m).
        final List<TrainLocation> survivors = trainLocationRepository.findTrain(9001L, DEPARTURE_DATE);
        Assertions.assertEquals(1, survivors.size(), "on-track train should be persisted");
        Assertions.assertEquals(50, survivors.getFirst().speed.intValue());
        Assertions.assertEquals(11, survivors.getFirst().accuracy.intValue());
        Assertions.assertTrue(survivors.getFirst().isGpsLocation, "GPS-based position should have isGpsLocation=true");

        // Off-track train 9002 is dropped by the near-track filter.
        Assertions.assertEquals(0, trainLocationRepository.findTrain(9002L, DEPARTURE_DATE).size(),
                "off-track location should be dropped by the near-track filter");
    }
}
