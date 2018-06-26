package fi.livi.rata.avoindata.LiikeInterface.integration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.LiikeInterface.BaseTest;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaTapahtuma;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Ignore
public class TrainExtractingIntegrationTest extends BaseTest {
//    public static final String DIGITRAFFIC_URL = "http://rata-beta.digitraffic.fi/api/v1/schedules?departure_date=";
    public static final String DIGITRAFFIC_URL = "http://localhost:5000/api/v1/schedules?departure_date=";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Test
    @Transactional
    public void trainsShouldBeEqual() throws URISyntaxException, IOException {
        LocalDate start = LocalDate.of(2017, 6, 4);
        LocalDate end = LocalDate.of(2017, 6, 10);

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            logger.info("Testing: {}", date);
            final Map<String, Junapaiva> liikeTrainMap = getTrainsInLiike(date);
            final Map<String, JsonNode> digitrafficTrainMap = getTrainsInDigitraffic(date);

            assertLiikeTrainsAreInDigitraffic(liikeTrainMap, digitrafficTrainMap);
            assertDigitrafficTrainsAreInLiike(liikeTrainMap, digitrafficTrainMap);
            assertTrainContentsAreTheSame(liikeTrainMap, digitrafficTrainMap);
        }
    }

    private void assertTrainContentsAreTheSame(final Map<String, Junapaiva> liikeTrainMap,
            final Map<String, JsonNode> digitrafficTrainMap) {
        for (final String trainNumber : liikeTrainMap.keySet()) {
            final Junapaiva liikeTrain = liikeTrainMap.get(trainNumber);
            final JsonNode digitrafficTrain = digitrafficTrainMap.get(trainNumber);

            Assert.assertEquals(liikeTrain.id.junanumero, digitrafficTrain.get("trainNumber").asText());
            Assert.assertEquals(liikeTrain.id.lahtopvm, LocalDate.parse(digitrafficTrain.get("departureDate").asText()));
            Assert.assertEquals(liikeTrain.id.toString(), liikeTrain.jupaTila.equals("VOIMASSAOLEVA") ? false : true,
                    digitrafficTrain.get("cancelled").asBoolean());

            final List<JupaTapahtuma> liikeTimeTableRows = sortTimeTableRows(liikeTrain.jupaTapahtumas);
            final JsonNode digitrafficTimeTableRows = digitrafficTrain.get("timeTableRows");
            Assert.assertEquals(liikeTrain.id.toString(), liikeTimeTableRows.size(), digitrafficTimeTableRows.size());

            for (int i = 0; i < liikeTimeTableRows.size(); i++) {
                final JupaTapahtuma jupaTapahtuma = liikeTimeTableRows.get(i);
                final JsonNode dtTTr = digitrafficTimeTableRows.get(i);

                final String idString = jupaTapahtuma.id.toString();

                final boolean isCancelled = jupaTapahtuma.jupaTila.equals("VOIMASSAOLEVA") ? false : true;
                Assert.assertEquals(idString, isCancelled, dtTTr.get("cancelled").asBoolean());

                final String attapType = jupaTapahtuma.tyyppi.equals("LAHTO") ? "DEPARTURE" : "ARRIVAL";
                Assert.assertEquals(idString, attapType, dtTTr.get("type").asText());

//                final ZonedDateTime scheduledTime = ZonedDateTime.parse(dtTTr.get("scheduledTime").asText()).withZoneSameInstant(
//                        ZoneId.of("Europe/Helsinki"));
//                Assert.assertEquals(idString, jupaTapahtuma.suunniteltuAika, scheduledTime);

                Assert.assertEquals(idString, jupaTapahtuma.liikennepaikka.lyhenne, dtTTr.get("stationShortCode").asText());

                if (i % 2 == 0 && i != 0 && i != liikeTimeTableRows.size() - 1) {
                    final boolean isCommercial = dtTTr.get("commercialStop") == null ? false : dtTTr.get("commercialStop").asBoolean();
                    Assert.assertEquals(idString, jupaTapahtuma.kaupallinen, isCommercial);
                }
            }
        }
    }

    private List<JupaTapahtuma> sortTimeTableRows(final Set<JupaTapahtuma> timeTableRows) {
        return timeTableRows.stream().sorted((o1, o2) -> {
            final int i = o1.suunniteltuAika.compareTo(o2.suunniteltuAika);
            if (i == 0) {
                return o2.tyyppi.compareTo(o1.tyyppi);
            } else {
                return i;
            }
        }).collect(Collectors.toList());
    }

    private void assertDigitrafficTrainsAreInLiike(final Map<String, Junapaiva> liikeTrainMap,
            final Map<String, JsonNode> digitrafficTrainMap) {
        for (final String trainNumber : digitrafficTrainMap.keySet()) {
            final Junapaiva jp = liikeTrainMap.get(trainNumber);
            Assert.assertNotNull("Train not found in digitraffic: " + trainNumber, jp);
        }
    }

    private void assertLiikeTrainsAreInDigitraffic(final Map<String, Junapaiva> liikeTrainMap,
            final Map<String, JsonNode> digitrafficTrainMap) {
        for (final String junanumero : liikeTrainMap.keySet()) {
            final JsonNode jsonNode = digitrafficTrainMap.get(junanumero);
            Assert.assertNotNull("Train not found in digitraffic " + junanumero, jsonNode);
            Assert.assertEquals(junanumero, jsonNode.get("trainNumber").asText());
        }
    }

    private Map<String, Junapaiva> getTrainsInLiike(final LocalDate departureDate) {
        final List<Junapaiva> trainsInLiike = junapaivaRepository.findByLahtoPvm(departureDate);
        return Maps.uniqueIndex(trainsInLiike, s -> s.id.junanumero);
    }

    private Map<String, JsonNode> getTrainsInDigitraffic(final LocalDate departureDate) throws URISyntaxException, IOException {
        URI jsonUrl = new URI(DIGITRAFFIC_URL + departureDate);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode trainsInDigitraffic = mapper.readTree(jsonUrl.toURL());

        return Maps.uniqueIndex(trainsInDigitraffic, s -> s.get("trainNumber").asText());
    }
}
