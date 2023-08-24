package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.time.LocalDate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Component
public class ForecastDeserializer extends AEntityDeserializer<Forecast> {
    @PersistenceContext
    private EntityManager entityManager;

    private Logger log = LoggerFactory.getLogger(ForecastDeserializer.class);

    @Override
    public Forecast deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        Forecast forecast = new Forecast();
        forecast.id = node.get("id").asLong();
        forecast.source = getNullableString(node, "lahde");
        forecast.version = node.get("version").asLong();

        final long attapId = node.get("jp_id").asLong();
        final LocalDate departureDate = getNodeAsLocalDate(node.get("jp_lahtopvm"));
        final long trainNumber = node.get("jp_junanumero").asLong();
        final TimeTableRowId timeTableRowId = new TimeTableRowId(attapId, departureDate, trainNumber);

        if (node.get("ennusteaika") != null) {
            forecast.forecastTime = getNodeAsDateTime(node.get("ennusteaika"));
            forecast.difference = node.get("poikkeama").asInt();
        } else {
            log.info("Received unknownDelay for timeTableRow {}", timeTableRowId);
        }

        forecast.lastModified = getNodeAsDateTime(node.get("muokkauspvm"));

        forecast.timeTableRow = entityManager.getReference(TimeTableRow.class, timeTableRowId);

        return forecast;
    }
}