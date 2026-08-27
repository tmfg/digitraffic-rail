package fi.livi.rata.avoindata.server.controller.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainLocationFactory;

@Transactional
public class TrainLocationControllerTest extends MockMvcBaseTest {
    private static final String MILLIS_TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

    @Autowired
    private TrainLocationFactory trainLocationFactory;

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    private TrainLocationId recentId(final long trainNumber) {
        return new TrainLocationId(trainNumber, DateProvider.dateInHelsinki(), DateProvider.nowInHelsinki());
    }

    @Test
    public void baseAttributesShouldBeCorrect() throws Exception {
        final TrainLocation trainLocation = trainLocationFactory.createTrainLocation();

        final ResultActions r1 = getJson("/train-locations/latest");

        r1.andExpect(jsonPath("$.length()").value(1));
        r1.andExpect(jsonPath("$[0].trainNumber").value(1L));
        r1.andExpect(jsonPath("$[0].departureDate").value(trainLocation.trainLocationId.departureDate.toString()));

        r1.andExpect(jsonPath("$[0].speed").value(trainLocation.speed));

        r1.andExpect(jsonPath("$[0].location.coordinates[0]").value(trainLocation.location.getX()));
        r1.andExpect(jsonPath("$[0].location.coordinates[1]").value(trainLocation.location.getY()));
    }

    @Test
    public void trainNumberFilteringShouldWork() throws Exception {
        trainLocationFactory.createTrainLocation();

        getJson("/train-locations/latest/1").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/2").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void geoJsonWorks() throws Exception {
        trainLocationFactory.createTrainLocation();

        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.length()").value(5))
                .andExpect(jsonPath("$.features[0].properties['trainNumber']").value(1))
                .andExpect(jsonPath("$.features[0].properties['departureDate']").value(DateProvider.dateInHelsinki().toString()))
                .andExpect(jsonPath("$.features[0].properties['speed']").value(100))
                .andExpect(jsonPath("$.features[0].properties['timestamp']").exists());
        getGeoJson("/train-locations.geojson/latest/1").andExpect(jsonPath("$.features.length()").value(1));
        getJson("/train-locations/latest/1").andExpect(jsonPath("$.features").doesNotExist());
    }

    @Test
    public void trainIdFilteringShouldWork() throws Exception {
        final LocalDate dateInHelsinki = DateProvider.dateInHelsinki();
        trainLocationFactory.createTrainLocation(new TrainLocationId(1L, dateInHelsinki, DateProvider.nowInHelsinki()));

        getJson(String.format("/train-locations/%s/1", dateInHelsinki)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/train-locations/%s/2", dateInHelsinki)).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/train-locations/%s/1", dateInHelsinki.minusDays(1))).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/train-locations/%s/1", dateInHelsinki.plusDays(1))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void boundingBoxShouldWork() throws Exception {
        trainLocationFactory.createTrainLocation(); // 20.3, 10.1

        getJson("/train-locations/latest/1?bbox=1,1,70,70").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/1?bbox=20.0, 10.0,21,11").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/1?bbox=19.0, 20.0,21,11").andExpect(jsonPath("$.length()").value(0));

        getJson("/train-locations/latest?bbox=1,1,70,70").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest?bbox=20.0, 10.0,21,11").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest?bbox=19.0, 11.0,21,11").andExpect(jsonPath("$.length()").value(0));

    }

    @Test
    public void timestampShouldAlwaysIncludeMilliseconds() throws Exception {
        trainLocationFactory.createTrainLocation();

        // Timestamp must always use format yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (exactly 3 decimal places)
        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].timestamp", matchesPattern(MILLIS_TIMESTAMP_REGEX)));
    }

    @Test
    public void accuracyShouldAppearAsIntegerWhenNonNull() throws Exception {
        trainLocationFactory.createTrainLocation(recentId(1L), 100, 5);

        // Also pins that serialization applies no unit transform (entity 5 -> API 5).
        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].accuracy").value(5));
    }

    @Test
    public void accuracyShouldBeAbsentWhenNull() throws Exception {
        trainLocationFactory.createTrainLocation(recentId(1L), 100, null);

        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].accuracy").doesNotExist());
    }

    @Test
    public void locationShouldBeGeoJsonPoint() throws Exception {
        trainLocationFactory.createTrainLocation();

        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].location.type").value("Point"))
                .andExpect(jsonPath("$[0].location.coordinates[0]").value(20.3))  // longitude first
                .andExpect(jsonPath("$[0].location.coordinates[1]").value(10.1)); // latitude second
    }

    @Test
    public void internalFieldsShouldBeExcluded() throws Exception {
        trainLocationFactory.createTrainLocation();

        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].locationEpsg3067").doesNotExist());
    }

    @Test
    public void geoJsonStructureShouldBeCorrect() throws Exception {
        trainLocationFactory.createTrainLocation();

        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(20.3))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(10.1))
                .andExpect(jsonPath("$.features[0].properties['timestamp']", matchesPattern(MILLIS_TIMESTAMP_REGEX)));
    }

    @Test
    public void geoJsonAccuracyPresentWhenNonNull() throws Exception {
        trainLocationFactory.createTrainLocation(recentId(1L), 100, 5);

        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features[0].properties['accuracy']").value(5));
    }

    @Test
    public void geoJsonAccuracyAbsentWhenNull() throws Exception {
        trainLocationFactory.createTrainLocation(recentId(1L), 100, null);

        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features[0].properties['accuracy']").doesNotExist());
    }

    // --- Test 13: isGpsLocation in REST V1 JSON response ---

    @Test
    public void isGpsLocationTrueShouldAppearInJsonResponse() throws Exception {
        // given — entity with isGpsLocation = true
        trainLocationFactory.createTrainLocation(recentId(1L), 100, 5, true);

        // when/then — V1 JSON response should contain isGpsLocation: true
        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].isGpsLocation").value(true));
    }

    @Test
    public void isGpsLocationFalseShouldAppearInJsonResponse() throws Exception {
        // given — entity with isGpsLocation = false
        trainLocationFactory.createTrainLocation(recentId(1L), 100, null, false);

        // when/then — V1 JSON response should contain isGpsLocation: false
        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].isGpsLocation").value(false));
    }

    @Test
    public void isGpsLocationShouldAlwaysBePresent() throws Exception {
        // given — default entity (isGpsLocation defaults to true)
        trainLocationFactory.createTrainLocation();

        // when/then — field is always present (NOT NULL), never absent
        getJson("/train-locations/latest")
                .andExpect(jsonPath("$[0].isGpsLocation").exists());
    }

    // --- Test 14: isGpsLocation in GeoJSON properties ---

    @Test
    public void geoJsonPropertiesShouldContainIsGpsLocation() throws Exception {
        // given
        trainLocationFactory.createTrainLocation(recentId(1L), 100, 5, true);

        // when/then — GeoJSON properties should include isGpsLocation
        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features[0].properties['isGpsLocation']").value(true));
    }

    @Test
    public void geoJsonPropertiesShouldContainIsGpsLocationFalse() throws Exception {
        // given
        trainLocationFactory.createTrainLocation(recentId(1L), 100, null, false);

        // when/then
        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features[0].properties['isGpsLocation']").value(false));
    }
}
