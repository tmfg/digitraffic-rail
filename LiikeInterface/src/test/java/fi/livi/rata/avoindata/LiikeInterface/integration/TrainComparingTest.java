package fi.livi.rata.avoindata.LiikeInterface.integration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.LiikeInterface.BaseTest;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;

@Ignore
public class TrainComparingTest extends BaseTest {
    //    public static final String DIGITRAFFIC_URL = "http://rata-beta.digitraffic.fi/api/v1/schedules?departure_date=";
    public static final String PRD1_URL = "http://front-prd.integraatiot.eu/api/v1/schedules?departure_date=";
    public static final String PRD2_URL = "http://front-prd2.integraatiot.eu/api/v1/schedules?departure_date=";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Test
    public void trainsShouldBeEqual() throws IOException, URISyntaxException {
        LocalDate start = LocalDate.of(2017, 6, 5);
        LocalDate end = LocalDate.of(2017, 6, 15);

        for (LocalDate date = start; date.isBefore(end) || date.isEqual(end); date = date.plusDays(1)) {
            logger.info("Testing: {}", date);

            final Map<Long, JsonNode> prd1TrainMap = getTrainsInDigitraffic(PRD1_URL, date);
            final Map<Long, JsonNode> prd2TrainMap = getTrainsInDigitraffic(PRD2_URL, date);

            for (final Long trainNumber : prd2TrainMap.keySet()) {
                final JsonNode prd1Train = prd1TrainMap.get(trainNumber);
                final JsonNode prd2Train = prd2TrainMap.get(trainNumber);
                if (prd1Train == null) {
                    logger.error("Train {} not found in prd1. Cancelled: {}", trainNumber, prd2Train.get("cancelled").asBoolean());
                } else {
                    assertTrainsEqual(prd1Train, prd2Train);
                }
            }

            for (final Long trainNumber : prd1TrainMap.keySet()) {
                final JsonNode prd1Train = prd1TrainMap.get(trainNumber);
                final JsonNode prd2Train = prd2TrainMap.get(trainNumber);
                if (prd2Train == null) {
                    logger.error("Train {} not found in prd2. Cancelled: {}", trainNumber, prd1Train.get("cancelled").asBoolean());
                } else {
                    assertTrainsEqual(prd1Train, prd2Train);
                }
            }
        }

    }

    private void assertTrainsEqual(final JsonNode prd1Train, final JsonNode prd2Train) {
        final JsonNode prd1TimeTableRows = prd1Train.get("timeTableRows");
        final JsonNode prd2TimeTableRows = prd2Train.get("timeTableRows");
        final String trainNumber = prd1Train.get("trainNumber").asText();
        Assert.assertEquals(trainNumber, prd1TimeTableRows.size(), prd2TimeTableRows.size());

        for (int i = 0; i < prd1TimeTableRows.size(); i++) {
            final JsonNode prd1TimeTableRow = prd1TimeTableRows.get(i);
            final JsonNode prd2TimeTableRow = prd2TimeTableRows.get(i);

            //            Assert.assertEquals(trainNumber, prd1TimeTableRow.get("cancelled").asBoolean(), prd2TimeTableRow.get
            // ("cancelled").asBoolean());
            Assert.assertEquals(trainNumber, prd1TimeTableRow.get("type").asText(), prd2TimeTableRow.get("type").asText());
            Assert.assertEquals(trainNumber, prd1TimeTableRow.get("scheduledTime").asText(),
                    prd2TimeTableRow.get("scheduledTime").asText());
        }
    }

    private Map<Long, JsonNode> getTrainsInDigitraffic(String baseUrl,
            final LocalDate departureDate) throws URISyntaxException, IOException {
        URI jsonUrl = new URI(baseUrl + departureDate);
        ObjectMapper mapper = new ObjectMapper();
        final URL url = jsonUrl.toURL();
//        logger.info("Fetching from {}", url);
        JsonNode trainsInDigitraffic = mapper.readTree(url);

        return Maps.uniqueIndex(trainsInDigitraffic, s -> s.get("trainNumber").asLong());
    }
}
