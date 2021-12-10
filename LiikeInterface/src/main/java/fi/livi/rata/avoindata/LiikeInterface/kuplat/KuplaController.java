package fi.livi.rata.avoindata.LiikeInterface.kuplat;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.services.ClassifiedTrainFilter;

@Controller
public class KuplaController {
    private class SijaintiDto {
        public final Integer longitude;
        public final Integer latitude;

        public SijaintiDto(Integer longitude, Integer latitude) {
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    private class KuplaDto {
        public final Integer junanumero;
        public final LocalDate lahtopaiva;
        public final Integer nopeus;
        public final ZonedDateTime aikaleima;
        public final SijaintiDto sijainti;

        public KuplaDto(Integer junanumero, LocalDate lahtopaiva, Integer nopeus, ZonedDateTime aikaleima, SijaintiDto sijainti) {
            this.junanumero = junanumero;
            this.lahtopaiva = lahtopaiva;
            this.nopeus = nopeus;
            this.aikaleima = aikaleima;
            this.sijainti = sijainti;
        }
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassifiedTrainFilter classifiedTrainFilter;

    @Value("${liike.base-url}")
    private String liikeBaseUrl;

    @Value("${kupla.url}")
    private String kuplaUrl;

    @RequestMapping(value = "/avoin/kuplas")
    @ResponseBody
    public Iterable<KuplaDto> getByVersion() throws IOException {
        final JsonNode kuplat = objectMapper.readTree(new URL(liikeBaseUrl + kuplaUrl));

        final JsonNode kuplatNodes = kuplat.get("kuplat");

        Iterable<JsonNode> filteredNullLocations = Iterables.filter(kuplatNodes, s -> {
            if (s.get("sijainti") != null && s.get("sijainti").get("latitude") != null) {
                return true;
            } else {
                log.trace("Filtered train location because location is null {}", s);
                return false;
            }
        });

        List<KuplaDto> kuplaDtoList = new ArrayList<>();
        for (JsonNode kuplatNode : filteredNullLocations) {
            SijaintiDto sijaintiDto = new SijaintiDto(kuplatNode.get("sijainti").get("longitude").asInt(), kuplatNode.get("sijainti").get("latitude").asInt());

            KuplaDto kuplaDto = new KuplaDto(
                    kuplatNode.get("junanumero").asInt(),
                    LocalDate.parse(kuplatNode.get("lahtopaiva").asText()),
                    kuplatNode.get("nopeus").asInt(),
                    ZonedDateTime.parse(kuplatNode.get("aikaleima").asText()),
                    sijaintiDto);
            kuplaDtoList.add(kuplaDto);
        }

        return classifiedTrainFilter.filterClassifiedTrains(
                kuplaDtoList,
                n -> new JunapaivaPrimaryKey(n.junanumero.toString(), n.lahtopaiva));
    }
}
