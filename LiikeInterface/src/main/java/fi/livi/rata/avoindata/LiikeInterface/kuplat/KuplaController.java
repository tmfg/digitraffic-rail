package fi.livi.rata.avoindata.LiikeInterface.kuplat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.services.ClassifiedTrainFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;

@Controller
public class KuplaController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassifiedTrainFilter classifiedTrainFilter;

    @Value("${kupla.url}")
    private String kuplaUrl;

    @RequestMapping(value = "/avoin/kuplas")
    @ResponseBody
    public Iterable<JsonNode> getByVersion() throws IOException {
        final JsonNode kuplat = objectMapper.readTree(new URL(kuplaUrl));

        final JsonNode kuplatNodes = kuplat.get("kuplat");

        final Iterable<JsonNode> filteredJunapaivaPrimaryKeys = classifiedTrainFilter.filterClassifiedTrains(
                Lists.newArrayList(kuplatNodes.elements()),
                n -> new JunapaivaPrimaryKey(n.get("junanumero").asText(), LocalDate.parse(n.get("lahtopaiva").asText())));

        Iterable<JsonNode> filteredNullLocations = Iterables.filter(filteredJunapaivaPrimaryKeys, s -> {
            if (s.get("sijainti") != null && s.get("sijainti").get("latitude") != null) {
                return true;
            } else {
                log.info("Filtered train location because location is null {}", s);
                return false;
            }
        });

        return Lists.newArrayList(filteredNullLocations);
    }
}
