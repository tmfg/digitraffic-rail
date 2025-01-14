package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.metadata.StationTypeEnum;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class StationControllerTest extends MockMvcBaseTest {
    @Autowired
    private StationRepository stationRepository;

    private Station createTestStation() {
        final var station = new Station();

        station.type = StationTypeEnum.STATION;
        station.passengerTraffic = true;
        station.id = 12345L;
        station.name = "TestStation";
        station.shortCode = "TS";
        station.latitude = BigDecimal.valueOf(22.123456789);
        station.longitude = BigDecimal.valueOf(32.123456789);

        return station;
    }

    @Test
    @Transactional
    public void geoJsonWorks() throws Exception {
        stationRepository.save(createTestStation());

        getGeoJson("/metadata/stations.geojson")
            .andExpect(jsonPath("$.features").exists())
            .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
            .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value("32.123457"));
    }
    @Test
    @Transactional
    public void jsonWorks() throws Exception {
        stationRepository.save(createTestStation());

        getJson("/metadata/stations")
            .andExpect(jsonPath("$.features").doesNotExist())
            .andExpect(jsonPath("$[0]").exists())
            .andExpect(jsonPath("$[0].longitude").value("32.123457"));
    }
}
