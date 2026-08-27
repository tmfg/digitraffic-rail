package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;

/**
 * Tests for {@link PalaYksikkoDeserializer} — the PALA {@code /0.2/yksikot.json} response parser.
 */
public class PalaYksikkoDeserializerTest extends BaseTest {

    @Autowired
    private PalaYksikkoDeserializer palaYksikkoDeserializer;

    private String loadFixture(final String name) throws IOException {
        return new String(new ClassPathResource(name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // --- Inline JSON builder for edge-case tests ---

    /**
     * Builds a single-train PALA JSON response with customisable fields.
     *
     * @param trainNumber    train number (also used as the object key)
     * @param nopeus         speed value; {@code null} produces JSON {@code null}
     * @param epavarmuus     top-level epavarmuus value
     * @param koordinaatti   JSON string for {@code sijainti.koordinaatti}, e.g. {@code "[385754, 6672611]"}; {@code null} omits the field
     * @param lahdeKuplaJson JSON string for the {@code lahdeKupla} array, e.g. {@code "[]"}
     */
    private static String buildPalaJson(final long trainNumber, final Integer nopeus, final int epavarmuus,
                                        final String koordinaatti, final String lahdeKuplaJson) {
        final String nopeusStr = nopeus != null ? String.valueOf(nopeus) : "null";
        final String koordinaattiField = koordinaatti != null
                ? "\"koordinaatti\": " + koordinaatti + ","
                : "";
        return """
                {
                  "%d": {
                    "junanumero": %d,
                    "lahtopaiva": "2026-07-06",
                    "aikaleima": "2026-07-06T08:34:29Z",
                    "nopeus": %s,
                    "epavarmuus": %d,
                    "sijainti": {
                      %s
                      "raideosuudet": [],
                      "lhraiteet": [],
                      "toimialueet": []
                    },
                    "lahdeKupla": %s,
                    "lahdeKulkutiedot": []
                  }
                }
                """.formatted(trainNumber, trainNumber, nopeusStr, epavarmuus, koordinaattiField, lahdeKuplaJson);
    }

    private static String gpsLahdeKupla(final int tarkkuus) {
        return """
                [{
                  "aikaleima": "2026-07-06T08:34:28Z",
                  "tunniste": "1.2.246.586.1.99.12345",
                  "koordinaatti": [380012.416714, 6736100.100908],
                  "nopeus": 74,
                  "tarkkuus": %d
                }]
                """.formatted(tarkkuus);
    }

    private static final String EMPTY_LAHDE_KUPLA = "[]";

    private static final String LAHDE_KUPLA_WITHOUT_KOORDINAATTI = """
            [{
              "aikaleima": "2026-07-06T08:34:28Z",
              "tunniste": "1.2.246.586.1.99.12345"
            }]
            """;

    // ========================================================================
    // Test 1: Object-keyed response format
    // ========================================================================

    @Test
    public void shouldParseMultipleTrainsFromObjectKeyedResponse() throws Exception {
        // given — PALA response with 2 train entries keyed by train number string
        final String json = loadFixture("pala-yksikko-multiple.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — both trains are parsed
        Assertions.assertEquals(2, result.size());
    }

    @Test
    public void eachTrainNumberShouldMatchObjectKey() throws Exception {
        // given
        final String json = loadFixture("pala-yksikko-multiple.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — result contains trains 9931 and 23 (from the object keys)
        final Set<Long> trainNumbers = result.stream()
                .map(tl -> tl.trainLocationId.trainNumber)
                .collect(Collectors.toSet());
        Assertions.assertTrue(trainNumbers.contains(9931L));
        Assertions.assertTrue(trainNumbers.contains(23L));
    }

    // ========================================================================
    // Test 2: GPS-based position deserialization
    // ========================================================================

    @Test
    public void shouldCorrectlyDeserializeGpsBasedPosition() throws Exception {
        // given — GPS-based position with lahdeKupla non-empty
        final String json = loadFixture("pala-yksikko-gps.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        final TrainLocation tl = result.getFirst();

        // junanumero → trainNumber
        Assertions.assertEquals(9931L, tl.trainLocationId.trainNumber.longValue());
        // lahtopaiva → departureDate
        Assertions.assertEquals(LocalDate.of(2026, 7, 6), tl.trainLocationId.departureDate);
        // aikaleima → timestamp (preserved as UTC)
        Assertions.assertEquals(ZonedDateTime.of(2026, 7, 6, 8, 34, 29, 0, ZoneId.of("UTC")),
                tl.trainLocationId.timestamp.withZoneSameInstant(ZoneId.of("UTC")));
        // nopeus → speed
        Assertions.assertEquals(74, tl.speed.intValue());
        // lahdeKupla[0].tarkkuus (5000 mm) → accuracy (5 m)
        Assertions.assertEquals(5, tl.accuracy.intValue());
        // isGpsLocation = true (lahdeKupla non-empty with koordinaatti)
        Assertions.assertTrue(tl.isGpsLocation);
    }

    // ========================================================================
    // Test 3: Calculated position (no GPS)
    // ========================================================================

    @Test
    public void shouldDeserializeCalculatedPositionWithoutGps() throws Exception {
        // given — calculated position: lahdeKupla is empty, lahdeKulkutiedot present
        final String json = loadFixture("pala-yksikko-calculated.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        final TrainLocation tl = result.getFirst();

        Assertions.assertNull(tl.accuracy, "Calculated position should have null accuracy");
        // isGpsLocation = false (lahdeKupla is empty)
        Assertions.assertFalse(tl.isGpsLocation, "Calculated position should have isGpsLocation=false");
        // coordinates should still be valid (from sijainti.koordinaatti)
        Assertions.assertNotNull(tl.location, "Calculated position should still have a location");
        Assertions.assertNotNull(tl.locationEpsg3067, "Calculated position should have locationEpsg3067 for filter");
    }

    // ========================================================================
    // Test 4: Coordinate conversion (EPSG:3067 → WGS84)
    // ========================================================================

    @Test
    public void shouldConvertEpsg3067ToWgs84() throws Exception {
        // given — GPS fixture uses EPSG:3067 coordinates [380012.416714, 6736100.100908]
        //         which convert to a known WGS84 point via Wgs84ConversionService
        final String json = loadFixture("pala-yksikko-gps.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — location (persisted, WGS84) matches known conversion result
        Assertions.assertEquals(1, result.size());
        final TrainLocation tl = result.getFirst();

        Assertions.assertEquals(24.799053, tl.location.getX(), 0.00001, "WGS84 longitude");
        Assertions.assertEquals(60.742345, tl.location.getY(), 0.00001, "WGS84 latitude");
    }

    @Test
    public void shouldRetainEpsg3067InLocationEpsg3067() throws Exception {
        // given
        final String json = loadFixture("pala-yksikko-gps.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — locationEpsg3067 (transient) holds original EPSG:3067 for the near-track filter
        Assertions.assertEquals(1, result.size());
        final TrainLocation tl = result.getFirst();

        Assertions.assertEquals(380012.416714, tl.locationEpsg3067.getX(), 0.001, "EPSG:3067 X");
        Assertions.assertEquals(6736100.100908, tl.locationEpsg3067.getY(), 0.001, "EPSG:3067 Y");
    }

    // ========================================================================
    // Test 5: Null speed handling — null nopeus = "unknown" → drop unit
    // ========================================================================

    @Test
    public void nullSpeedShouldDropUnit() throws Exception {
        // given — PALA null nopeus means "speed unknown"; our speed column is NOT NULL so the unit is dropped
        final String json = buildPalaJson(8050L, null, 5, "[385754, 6672611]", gpsLahdeKupla(5000));

        // when
        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(json);

        // then — unit is dropped and counted, not defaulted to speed 0
        Assertions.assertEquals(1, result.receivedCount());
        Assertions.assertTrue(result.locations().isEmpty(), "unit with unknown speed is dropped");
        Assertions.assertEquals(1, result.droppedNoSpeed());
        Assertions.assertEquals(0, result.deserializationErrors());
    }

    @Test
    public void nonNullSpeedShouldMapDirectly() throws Exception {
        // given — GPS fixture has nopeus: 74
        final String json = loadFixture("pala-yksikko-gps.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(74, result.getFirst().speed.intValue());
    }

    @Test
    public void explicitZeroSpeedShouldRemainZero() throws Exception {
        // given — explicit nopeus: 0
        final String json = buildPalaJson(8001L, 0, 5, "[385754, 6672611]", gpsLahdeKupla(5000));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(0, result.getFirst().speed.intValue());
    }

    // ========================================================================
    // Test 6: Accuracy capped at MAX_ACCURACY
    // ========================================================================

    @Test
    public void normalAccuracyShouldConvertFromMmToMeters() throws Exception {
        // given — tarkkuus: 5000 mm → 5 m
        final String json = buildPalaJson(8010L, 74, 5, "[385754, 6672611]", gpsLahdeKupla(5000));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(5, result.getFirst().accuracy.intValue());
    }

    @Test
    public void accuracyAtCapShouldBeExactlyMaxAccuracy() throws Exception {
        // given — tarkkuus: 32000000 mm = 32000 m (exactly at cap)
        final String json = buildPalaJson(8011L, 74, 5, "[385754, 6672611]", gpsLahdeKupla(32000000));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(PalaYksikkoDeserializer.MAX_ACCURACY, result.getFirst().accuracy.intValue());
    }

    @Test
    public void accuracyAboveCapShouldBeClampedToMaxAccuracy() throws Exception {
        // given — tarkkuus: 99999999 mm → would be 99999 m, clamped to 32000 m
        final String json = buildPalaJson(8012L, 74, 5, "[385754, 6672611]", gpsLahdeKupla(99999999));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(PalaYksikkoDeserializer.MAX_ACCURACY, result.getFirst().accuracy.intValue());
    }

    @Test
    public void zeroAccuracyShouldBeZero() throws Exception {
        // given — tarkkuus: 0 mm → 0 m
        final String json = buildPalaJson(8013L, 74, 5, "[385754, 6672611]", gpsLahdeKupla(0));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(0, result.getFirst().accuracy.intValue());
    }

    @Test
    public void emptyLahdeKuplaShouldResultInNullAccuracy() throws Exception {
        // given — lahdeKupla is empty → no GPS, no accuracy
        final String json = buildPalaJson(8014L, 74, 416, "[385754, 6672611]", EMPTY_LAHDE_KUPLA);

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertNull(result.getFirst().accuracy, "Empty lahdeKupla should produce null accuracy");
    }

    // ========================================================================
    // Test 7: isGpsLocation flag
    // ========================================================================

    @Test
    public void nonEmptyLahdeKuplaWithKoordinaattiShouldBeGps() throws Exception {
        // given — lahdeKupla has entry with koordinaatti
        final String json = loadFixture("pala-yksikko-gps.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.getFirst().isGpsLocation);
    }

    @Test
    public void emptyLahdeKuplaShouldNotBeGps() throws Exception {
        // given — lahdeKupla is empty array []
        final String json = loadFixture("pala-yksikko-calculated.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertFalse(result.getFirst().isGpsLocation);
    }

    @Test
    public void lahdeKuplaWithoutKoordinaattiShouldNotBeGps() throws Exception {
        // given — lahdeKupla has entry but no koordinaatti (GPS device present but no fix)
        final String json = buildPalaJson(8020L, 74, 5, "[385754, 6672611]", LAHDE_KUPLA_WITHOUT_KOORDINAATTI);

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertEquals(1, result.size());
        Assertions.assertFalse(result.getFirst().isGpsLocation,
                "lahdeKupla entry without koordinaatti means no GPS fix → isGpsLocation=false");
    }

    // ========================================================================
    // Test 8: Empty response
    // ========================================================================

    @Test
    public void emptyResponseShouldReturnEmptyList() throws Exception {
        // given — empty JSON object (no trains running)
        final String json = loadFixture("pala-yksikko-empty.json");

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty(), "Empty PALA response should produce an empty list");
    }

    // ========================================================================
    // Test 9: Missing sijainti.koordinaatti drops unit
    // ========================================================================

    @Test
    public void missingKoordinaattiShouldDropUnit() throws Exception {
        // given — sijainti present but koordinaatti absent → unit should be excluded
        // Units without koordinaatti are dropped
        final String json = buildPalaJson(8030L, 74, 5, null, gpsLahdeKupla(5000));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then
        Assertions.assertTrue(result.isEmpty(),
                "Unit without sijainti.koordinaatti should be dropped");
    }

    @Test
    public void missingKoordinaattiShouldDropOnlyAffectedEntry() throws Exception {
        // given — 3 entries: 2 with koordinaatti, 1 without
        final String json = """
                {
                  "100": {
                    "junanumero": 100,
                    "lahtopaiva": "2026-07-06",
                    "aikaleima": "2026-07-06T08:00:00Z",
                    "nopeus": 50,
                    "epavarmuus": 5,
                    "sijainti": {
                      "koordinaatti": [385754, 6672611],
                      "raideosuudet": [], "lhraiteet": [], "toimialueet": []
                    },
                    "lahdeKupla": [{"aikaleima": "2026-07-06T08:00:00Z", "tunniste": "1.2.246.586.1.99.1", "koordinaatti": [385754, 6672611], "tarkkuus": 5000}],
                    "lahdeKulkutiedot": []
                  },
                  "200": {
                    "junanumero": 200,
                    "lahtopaiva": "2026-07-06",
                    "aikaleima": "2026-07-06T08:01:00Z",
                    "nopeus": 60,
                    "epavarmuus": 400,
                    "sijainti": {
                      "raideosuudet": [], "lhraiteet": [], "toimialueet": []
                    },
                    "lahdeKupla": [],
                    "lahdeKulkutiedot": []
                  },
                  "300": {
                    "junanumero": 300,
                    "lahtopaiva": "2026-07-06",
                    "aikaleima": "2026-07-06T08:02:00Z",
                    "nopeus": 80,
                    "epavarmuus": 3,
                    "sijainti": {
                      "koordinaatti": [327785, 6823456],
                      "raideosuudet": [], "lhraiteet": [], "toimialueet": []
                    },
                    "lahdeKupla": [{"aikaleima": "2026-07-06T08:02:00Z", "tunniste": "1.2.246.586.1.99.3", "koordinaatti": [327785, 6823456], "tarkkuus": 3000}],
                    "lahdeKulkutiedot": []
                  }
                }
                """;

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — train 200 (no koordinaatti) is dropped; trains 100 and 300 survive
        Assertions.assertEquals(2, result.size(), "Only entries with koordinaatti should be included");
        final Set<Long> trainNumbers = result.stream()
                .map(tl -> tl.trainLocationId.trainNumber)
                .collect(Collectors.toSet());
        Assertions.assertTrue(trainNumbers.contains(100L));
        Assertions.assertTrue(trainNumbers.contains(300L));
        Assertions.assertFalse(trainNumbers.contains(200L));
    }

    // ========================================================================
    // Test 10: epavarmuus is not exposed
    // ========================================================================

    @Test
    public void epavarmuusShouldNotAffectAccuracyForGpsPosition() throws Exception {
        // given — GPS position with epavarmuus: 100 and lahdeKupla[0].tarkkuus: 5000
        // accuracy derives from tarkkuus
        final String json = buildPalaJson(8040L, 74, 100, "[385754, 6672611]", gpsLahdeKupla(5000));

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — accuracy is 5 (from tarkkuus 5000mm / 1000), not 100 (epavarmuus)
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(5, result.getFirst().accuracy.intValue(),
                "accuracy must derive from lahdeKupla[].tarkkuus, not top-level epavarmuus");
    }

    @Test
    public void epavarmuusShouldNotAffectAccuracyForCalculatedPosition() throws Exception {
        // given — calculated position with epavarmuus: 416 and empty lahdeKupla
        // epavarmuus is ignored for public accuracy
        final String json = buildPalaJson(8041L, 74, 416, "[327785, 6823456]", EMPTY_LAHDE_KUPLA);

        // when
        final List<TrainLocation> result = palaYksikkoDeserializer.deserialize(json);

        // then — accuracy is null (no GPS data), not 416 (epavarmuus)
        Assertions.assertEquals(1, result.size());
        Assertions.assertNull(result.getFirst().accuracy,
                "calculated position accuracy must be null, not epavarmuus");
    }

    // ========================================================================
    // Test 11: Parse result stats — resilience + observability (deserializeWithStats)
    // ========================================================================

    @Test
    public void resultShouldReportRawReceivedCount() throws Exception {
        final String json = loadFixture("pala-yksikko-multiple.json");

        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(json);

        Assertions.assertEquals(2, result.receivedCount(), "receivedCount is the raw PALA object size");
        Assertions.assertEquals(2, result.locations().size());
        Assertions.assertEquals(0, result.deserializationErrors());
        Assertions.assertEquals(0, result.droppedNoCoordinate());
    }

    @Test
    public void missingKoordinaattiShouldBeCountedAsNoCoordinateDrop() throws Exception {
        // sijainti present but koordinaatti absent → legitimate "no position known" (RESOLVED PALA-Q3)
        final String json = buildPalaJson(8030L, 74, 5, null, gpsLahdeKupla(5000));

        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(json);

        Assertions.assertEquals(1, result.receivedCount());
        Assertions.assertTrue(result.locations().isEmpty());
        Assertions.assertEquals(1, result.droppedNoCoordinate(), "unit without koordinaatti is a no-coordinate drop");
        Assertions.assertEquals(0, result.deserializationErrors());
    }

    @Test
    public void malformedUnitShouldBeCountedAsErrorAndNotAbortBatch() throws Exception {
        // train 200 has an unparseable aikaleima; trains 100 and 300 are valid and must still survive
        final String json = """
                {
                  "100": {
                    "junanumero": 100, "lahtopaiva": "2026-07-06", "aikaleima": "2026-07-06T08:00:00Z",
                    "nopeus": 50, "epavarmuus": 5,
                    "sijainti": { "koordinaatti": [385754, 6672611], "raideosuudet": [], "lhraiteet": [], "toimialueet": [] },
                    "lahdeKupla": [], "lahdeKulkutiedot": []
                  },
                  "200": {
                    "junanumero": 200, "lahtopaiva": "2026-07-06", "aikaleima": "NOT-A-TIMESTAMP",
                    "nopeus": 60, "epavarmuus": 5,
                    "sijainti": { "koordinaatti": [385754, 6672611], "raideosuudet": [], "lhraiteet": [], "toimialueet": [] },
                    "lahdeKupla": [], "lahdeKulkutiedot": []
                  },
                  "300": {
                    "junanumero": 300, "lahtopaiva": "2026-07-06", "aikaleima": "2026-07-06T08:02:00Z",
                    "nopeus": 80, "epavarmuus": 3,
                    "sijainti": { "koordinaatti": [327785, 6823456], "raideosuudet": [], "lhraiteet": [], "toimialueet": [] },
                    "lahdeKupla": [], "lahdeKulkutiedot": []
                  }
                }
                """;

        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(json);

        Assertions.assertEquals(3, result.receivedCount());
        Assertions.assertEquals(2, result.locations().size(), "valid units survive; the bad one is skipped");
        Assertions.assertEquals(1, result.deserializationErrors());
        Assertions.assertEquals("200", result.firstErrorTrainNumber());
        Assertions.assertNotNull(result.firstErrorSampleJson(), "first error captures a JSON sample");
    }

    @Test
    public void missingRequiredFieldShouldBeCountedAsErrorNotNoCoordinateDrop() throws Exception {
        // koordinaatti present but junanumero missing → data-quality error, not a legitimate no-coordinate drop
        final String json = """
                {
                  "500": {
                    "lahtopaiva": "2026-07-06", "aikaleima": "2026-07-06T08:00:00Z", "nopeus": 50, "epavarmuus": 5,
                    "sijainti": { "koordinaatti": [385754, 6672611], "raideosuudet": [], "lhraiteet": [], "toimialueet": [] },
                    "lahdeKupla": [], "lahdeKulkutiedot": []
                  }
                }
                """;

        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(json);

        Assertions.assertTrue(result.locations().isEmpty());
        Assertions.assertEquals(1, result.deserializationErrors());
        Assertions.assertEquals(0, result.droppedNoCoordinate());
    }

    // ========================================================================
    // Test 12: Root must be the documented object-keyed response
    // ========================================================================

    @Test
    public void arrayRootShouldThrowContractFailure() {
        // A syntactically valid but non-object root ([]) is an upstream contract failure, not an empty poll.
        Assertions.assertThrows(IllegalStateException.class,
                () -> palaYksikkoDeserializer.deserializeWithStats("[]"));
    }

    @Test
    public void nullRootShouldThrowContractFailure() {
        Assertions.assertThrows(IllegalStateException.class,
                () -> palaYksikkoDeserializer.deserializeWithStats("null"));
    }

    @Test
    public void emptyObjectRootShouldRemainAValidEmptyPoll() {
        // {} is a valid "no trains" response and must NOT throw.
        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats("{}");
        Assertions.assertTrue(result.locations().isEmpty());
        Assertions.assertEquals(0, result.receivedCount());
    }

    // ========================================================================
    // Test 13: GPS classification requires a real coordinate; accuracy is coupled to that same source
    // ========================================================================

    @Test
    public void lahdeKuplaWithNullKoordinaattiShouldNotBeGps() throws Exception {
        // has("koordinaatti") is true even for JSON null; it must NOT be classified as a GPS fix.
        final String lahdeKupla = """
                [{ "aikaleima": "2026-07-06T08:34:28Z", "tunniste": "1.2.246.586.1.99.1", "koordinaatti": null, "tarkkuus": 5000 }]
                """;
        final String json = buildPalaJson(8070L, 74, 5, "[385754, 6672611]", lahdeKupla);

        final TrainLocation tl = palaYksikkoDeserializer.deserialize(json).getFirst();

        Assertions.assertFalse(tl.isGpsLocation, "JSON-null koordinaatti is not a GPS fix");
        Assertions.assertNull(tl.accuracy, "no GPS source → null accuracy (tarkkuus of a non-GPS source is ignored)");
    }

    @Test
    public void lahdeKuplaWithMalformedKoordinaattiShouldNotBeGps() throws Exception {
        // A single-element coordinate array is malformed → not GPS.
        final String lahdeKupla = """
                [{ "aikaleima": "2026-07-06T08:34:28Z", "tunniste": "1.2.246.586.1.99.1", "koordinaatti": [385754], "tarkkuus": 5000 }]
                """;
        final String json = buildPalaJson(8071L, 74, 5, "[385754, 6672611]", lahdeKupla);

        final TrainLocation tl = palaYksikkoDeserializer.deserialize(json).getFirst();

        Assertions.assertFalse(tl.isGpsLocation);
        Assertions.assertNull(tl.accuracy);
    }

    @Test
    public void accuracyShouldComeFromTheSourceThatSuppliedTheGpsCoordinate() throws Exception {
        // First source has no coordinate (tarkkuus 9999); the second supplies the GPS fix (tarkkuus 5000).
        // Accuracy must come from the second (5 m), not the unrelated first (9 m).
        final String lahdeKupla = """
                [
                  { "aikaleima": "2026-07-06T08:34:27Z", "tunniste": "1.2.246.586.1.99.a", "tarkkuus": 9999 },
                  { "aikaleima": "2026-07-06T08:34:28Z", "tunniste": "1.2.246.586.1.99.b", "koordinaatti": [385754, 6672611], "tarkkuus": 5000 }
                ]
                """;
        final String json = buildPalaJson(8072L, 74, 5, "[385754, 6672611]", lahdeKupla);

        final TrainLocation tl = palaYksikkoDeserializer.deserialize(json).getFirst();

        Assertions.assertTrue(tl.isGpsLocation);
        Assertions.assertEquals(5, tl.accuracy.intValue(),
                "accuracy must come from the GPS coordinate's own source entry, not lahdeKupla[0]");
    }

    @Test
    public void gpsSourceWithoutTarkkuusShouldHaveNullAccuracy() throws Exception {
        final String lahdeKupla = """
                [{ "aikaleima": "2026-07-06T08:34:28Z", "tunniste": "1.2.246.586.1.99.1", "koordinaatti": [385754, 6672611] }]
                """;
        final String json = buildPalaJson(8073L, 74, 5, "[385754, 6672611]", lahdeKupla);

        final TrainLocation tl = palaYksikkoDeserializer.deserialize(json).getFirst();

        Assertions.assertTrue(tl.isGpsLocation);
        Assertions.assertNull(tl.accuracy, "GPS fix without tarkkuus → null accuracy");
    }
}
