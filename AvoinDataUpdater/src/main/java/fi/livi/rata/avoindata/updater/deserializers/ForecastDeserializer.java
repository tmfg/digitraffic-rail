package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.time.LocalDate;

@Component
public class ForecastDeserializer extends AEntityDeserializer<Forecast> {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Forecast deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        Forecast forecast = new Forecast();
        forecast.id = node.get("id").asLong();
        forecast.source = getNullableString(node, "lahde");
        forecast.forecastTime = getNodeAsDateTime(node.get("ennusteaika"));
        forecast.difference = node.get("poikkeama").asInt();
        forecast.version = node.get("version").asLong();

        final long attapId = node.get("jp_id").asLong();
        final LocalDate departureDate = getNodeAsLocalDate(node.get("jp_lahtopvm"));
        final long trainNumber = node.get("jp_junanumero").asLong();
        final TimeTableRowId timeTableRowId = new TimeTableRowId(attapId, departureDate, trainNumber);

        forecast.timeTableRow = entityManager.getReference(TimeTableRow.class,timeTableRowId);

        return forecast;
    }
}