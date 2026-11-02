package fi.livi.rata.avoindata.server.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;

class SwaggerConfigIntegrationTest extends MockMvcBaseTest {

    @Test
    void lastUpdatedIsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rail-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/last-updated'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/last-updated'].get.responses['200'].content['application/json']"
                        + ".schema.additionalProperties.properties.isUpToDate").exists());
    }

    /**
     * The updater serves the endpoint, so the description is written by hand. The same field names are checked
     * against the real response in the updater's LastUpdatedResponseFormatTest: change both when the response changes.
     */
    @Test
    void lastUpdatedDescriptionHasTheSameFieldsAsTheResponse() throws Exception {
        final String schema = "$.paths['/api/v1/last-updated'].get.responses['200'].content['application/json']"
                + ".schema.additionalProperties.properties";
        mockMvc.perform(get("/v3/api-docs/rail-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(schema + ".length()").value(4))
                .andExpect(jsonPath(schema + ".lastUpdated.type").value("string"))
                .andExpect(jsonPath(schema + ".lastUpdated.format").value("date-time"))
                .andExpect(jsonPath(schema + ".alarmLimit.type").value("string"))
                .andExpect(jsonPath(schema + ".durationSinceLastUpdate.type").value("string"))
                .andExpect(jsonPath(schema + ".isUpToDate.type").value("boolean"));
    }

    @Test
    void swaggerUiIsServedAsHtml() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
