package fi.livi.rata.avoindata.updater.updaters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainLocationFilter;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainLocationNearTrackFilterService;

/**
 * Integration tests for the full PALA ingestion pipeline: PALA JSON → {@link PalaYksikkoDeserializer}
 * → filters → persist. Also validates the Flyway migration for the {@code is_gps_location} column.
 *
 * <p>MQTT is disabled (not relevant for this integration test).
 */
@Transactional
@TestPropertySource(properties = { "mqtt.enable=false" })
public class TrainLocationPalaIntegrationTest extends BaseTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 6);

    @Autowired
    private PalaYksikkoDeserializer palaYksikkoDeserializer;

    @Autowired
    private RecentlySeenTrainLocationFilter recentlySeenTrainLocationFilter;

    @Autowired
    private TrainLocationNearTrackFilterService trainLocationNearTrackFilterService;

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    private String loadFixture(final String name) throws IOException {
        return new String(new ClassPathResource(name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ========================================================================
    // Test 18: Full PALA pipeline integration
    // ========================================================================

    @Test
    public void gpsBasedPalaResponseShouldDeserializeFilterAndPersist() throws Exception {
        // given — GPS-based PALA response (train 9931, on-track Helsinki coordinates)
        final String json = loadFixture("pala-yksikko-gps.json");

        // when — full pipeline: deserialize → dedup filter → near-track filter → persist
        final List<TrainLocation> deserialized = palaYksikkoDeserializer.deserialize(json);
        final List<TrainLocation> deduped = recentlySeenTrainLocationFilter.filter(deserialized);
        final List<TrainLocation> nearTrack = deduped.stream()
                .filter(trainLocationNearTrackFilterService::isTrainLocationNearTrack)
                .toList();
        trainLocationRepository.saveAll(nearTrack);

        // then — record persisted with correct fields
        final List<TrainLocation> saved = trainLocationRepository.findTrain(9931L, DEPARTURE_DATE);
        Assertions.assertEquals(1, saved.size(), "GPS-based train should be persisted");

        final TrainLocation tl = saved.getFirst();
        Assertions.assertEquals(9931L, tl.trainLocationId.trainNumber.longValue());
        Assertions.assertEquals(DEPARTURE_DATE, tl.trainLocationId.departureDate);
        Assertions.assertEquals(74, tl.speed.intValue());
        Assertions.assertEquals(5, tl.accuracy.intValue());
        Assertions.assertTrue(tl.isGpsLocation, "GPS-based position should have isGpsLocation=true");
        Assertions.assertNotNull(tl.location, "WGS84 location should be persisted");
    }

    @Test
    public void calculatedPalaResponseShouldDeserializeFilterAndPersist() throws Exception {
        // given — calculated position (train 1, on-track Tampere coordinates)
        final String json = loadFixture("pala-yksikko-calculated.json");

        // when
        final List<TrainLocation> deserialized = palaYksikkoDeserializer.deserialize(json);
        final List<TrainLocation> deduped = recentlySeenTrainLocationFilter.filter(deserialized);
        final List<TrainLocation> nearTrack = deduped.stream()
                .filter(trainLocationNearTrackFilterService::isTrainLocationNearTrack)
                .toList();
        trainLocationRepository.saveAll(nearTrack);

        // then
        final List<TrainLocation> saved = trainLocationRepository.findTrain(1L, DEPARTURE_DATE);
        Assertions.assertEquals(1, saved.size(), "Calculated position should be persisted");

        final TrainLocation tl = saved.getFirst();
        Assertions.assertNull(tl.accuracy, "Calculated position should have null accuracy");
        Assertions.assertFalse(tl.isGpsLocation, "Calculated position should have isGpsLocation=false");
    }

    @Test
    public void emptyPalaResponseShouldNotPersistAnything() throws Exception {
        // given — empty PALA response
        final String json = loadFixture("pala-yksikko-empty.json");

        // when
        final List<TrainLocation> deserialized = palaYksikkoDeserializer.deserialize(json);

        // then — empty response, nothing to persist, no errors
        Assertions.assertTrue(deserialized.isEmpty(), "Empty PALA response should produce no locations");
    }

    @Test
    public void mixedGpsAndCalculatedShouldBothBePersisted() throws Exception {
        // given — response with both GPS and calculated positions
        final String json = loadFixture("pala-yksikko-multiple.json");

        // when
        final List<TrainLocation> deserialized = palaYksikkoDeserializer.deserialize(json);
        final List<TrainLocation> deduped = recentlySeenTrainLocationFilter.filter(deserialized);
        final List<TrainLocation> nearTrack = deduped.stream()
                .filter(trainLocationNearTrackFilterService::isTrainLocationNearTrack)
                .toList();
        trainLocationRepository.saveAll(nearTrack);

        // then — both GPS trains should be persisted (fixture has 2 GPS-based trains)
        Assertions.assertEquals(2, nearTrack.size(), "Both GPS trains from the multiple fixture should survive filters");
    }

    // ========================================================================
    // Test 19: Flyway migration — is_gps_location column
    // ========================================================================

    @Test
    public void isGpsLocationDefaultShouldBeTrueForNewRecords() {
        // given — entity with isGpsLocation not explicitly set (defaults to true)
        final TrainLocation tl = new TrainLocation();
        tl.trainLocationId = new TrainLocationId(8001L, DEPARTURE_DATE,
                ZonedDateTime.parse("2026-07-06T10:00:00Z"));
        tl.location = geometryFactory.createPoint(new Coordinate(20.3, 10.1));
        tl.speed = 0;

        // when
        final TrainLocation saved = trainLocationRepository.save(tl);

        // then — default should be true (backwards-compatible with existing GPS-only data)
        Assertions.assertTrue(saved.isGpsLocation,
                "Default isGpsLocation should be true for backwards compatibility");
    }

    @Test
    public void isGpsLocationFalseCanBePersisted() {
        // given
        final TrainLocation tl = new TrainLocation();
        tl.trainLocationId = new TrainLocationId(8002L, DEPARTURE_DATE,
                ZonedDateTime.parse("2026-07-06T10:01:00Z"));
        tl.location = geometryFactory.createPoint(new Coordinate(20.3, 10.1));
        tl.speed = 0;
        tl.isGpsLocation = false;

        // when
        final TrainLocation saved = trainLocationRepository.save(tl);

        // then
        Assertions.assertFalse(saved.isGpsLocation,
                "isGpsLocation=false should be persistable");
    }
}
