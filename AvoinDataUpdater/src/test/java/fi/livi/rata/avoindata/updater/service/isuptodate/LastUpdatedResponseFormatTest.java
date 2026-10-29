package fi.livi.rata.avoindata.updater.service.isuptodate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.common.domain.lastupdated.UpToDateInformation;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The OpenAPI description of /api/v1/last-updated is generated in the server application from the same
 * UpToDateInformation class. This test checks that the real JSON matches the description that is generated
 * (SwaggerConfigIntegrationTest): field names and the string formats of the time values.
 */
public class LastUpdatedResponseFormatTest extends BaseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void responseHasTheFieldsDescribedInOpenApi() {
        final Map<LastUpdatedType, UpToDateInformation> response = Map.of(LastUpdatedType.TRAINS,
                new UpToDateInformation(Instant.parse("2026-10-29T07:30:00Z"), Duration.ofMinutes(5),
                        Duration.ofMillis(1200)));

        final JsonNode information = objectMapper.valueToTree(response).get("TRAINS");

        assertEquals(new TreeSet<>(java.util.List.of("lastUpdated", "alarmLimit", "durationSinceLastUpdate", "isUpToDate")),
                new TreeSet<>(information.propertyNames()));
        assertEquals("2026-10-29T07:30:00Z", information.get("lastUpdated").asString());
        assertEquals("PT5M", information.get("alarmLimit").asString());
        assertEquals("PT1.2S", information.get("durationSinceLastUpdate").asString());
        assertTrue(information.get("isUpToDate").isBoolean());
    }
}
