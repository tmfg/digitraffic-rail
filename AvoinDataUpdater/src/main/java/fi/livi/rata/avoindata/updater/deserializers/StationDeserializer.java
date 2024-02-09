package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.metadata.StationTypeEnum;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;

@Component
public class StationDeserializer extends AEntityDeserializer<Station> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public Station deserialize(final JsonParser jsonParser,
                               final DeserializationContext deserializationContext) throws IOException {
        // To break circular reference
        var trakediaLiikennepaikkaMap = applicationContext.getBean(TrakediaLiikennepaikkaService.class).getTrakediaLiikennepaikkas(LocalDate.now().atStartOfDay(ZoneId.of("UTC")));

        Station station = new Station();

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        station.id = node.get("id").asLong();
        station.name = getStringFromNode(node, "nimi");
        station.shortCode = getStringFromNode(node, "lyhenne");
        station.countryCode = getStringFromNode(node, "maakoodi");
        station.uicCode = node.get("uicKoodi").asInt();
        station.passengerTraffic = node.get("matkustajaAikataulussa").asBoolean();

        station.type = getStationType(node);

        final ProjCoordinate to = getCoordinates(trakediaLiikennepaikkaMap, station, node);

        station.latitude = new BigDecimal(to.y);
        station.longitude = new BigDecimal(to.x);

        return station;
    }

    private ProjCoordinate getCoordinates(Map<String, Double[]> trakediaLiikennepaikkaMap, Station station, JsonNode node) {
        final Double[] koordinaatit = trakediaLiikennepaikkaMap.get(station.shortCode);

        final ProjCoordinate to;
        if (koordinaatit != null) {
            to = wgs84ConversionService.liviToWgs84(koordinaatit[0], koordinaatit[1]);
        } else {
            to = wgs84ConversionService.liviToWgs84(node.get("iKoordinaatti").asDouble(), node.get("pKoordinaatti").asDouble());
            log.info("Used Liike coordinates to change station {} coordinate from {}/{} to {}/{}",station.shortCode,node.get("iKoordinaatti").asDouble(),node.get("pKoordinaatti").asDouble(),to.x,to.y);
        }
        return to;
    }

    private StationTypeEnum getStationType(final JsonNode node) {
        final int lptypId = node.get("lptypId").asInt();
        if (lptypId == 1) {
            return StationTypeEnum.STATION;
        } else if (lptypId == 2) {
            return StationTypeEnum.STOPPING_POINT;
        } else if (lptypId == 3) {
            return StationTypeEnum.TURNOUT_IN_THE_OPEN_LINE;
        } else {
            throw new IllegalStateException("Unknown lptypId: " + lptypId);
        }
    }
}
