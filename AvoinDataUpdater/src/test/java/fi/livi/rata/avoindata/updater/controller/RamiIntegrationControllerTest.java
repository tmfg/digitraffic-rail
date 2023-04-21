package fi.livi.rata.avoindata.updater.controller;

import static fi.livi.rata.avoindata.updater.config.RamiApiKeyValidationFilter.INVALID_API_KEY_ERROR;
import static fi.livi.rata.avoindata.updater.controllers.RamiIntegrationController.BASE_PATH;
import static fi.livi.rata.avoindata.updater.controllers.RamiIntegrationController.BASE_PATH_ALTERNATE;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;

import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import fi.livi.rata.avoindata.updater.BaseTest;

@AutoConfigureMockMvc
public class RamiIntegrationControllerTest extends BaseTest {

    @Autowired
    private MockMvc mvc;


    private static String testApiKey;
    private static String validRamiMessage;
    private static String invalidRamiMessage;
    private final String validationError = "e2eId: is missing";

    @BeforeAll
    public static void setupTests(
            @Value("classpath:rami/validRamiMessage.json")
            final Resource validRamiMessageJson,
            @Value("classpath:rami/invalidRamiMessage.json")
            final Resource invalidRamiMessageJson,
            @Value("${rami.api-key}")
            final String testApiKeyValue) throws IOException {
        validRamiMessage = new String(Files.readAllBytes(validRamiMessageJson.getFile().toPath()));
        invalidRamiMessage = new String(Files.readAllBytes(invalidRamiMessageJson.getFile().toPath()));
        testApiKey = testApiKeyValue;
    }

    @Test
    public void correctApiKeyIsRequired() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH + "/message")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", "")
                        .content(validRamiMessage))
                .andExpect(content().string(INVALID_API_KEY_ERROR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void validRamiMessageReturns200() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH + "/message")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", testApiKey)
                        .content(validRamiMessage))
                .andExpect(content().string(""))
                .andExpect(status().isOk());
    }

    @Test
    public void invalidRamiMessageIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH + "/message")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", testApiKey)
                        .content(invalidRamiMessage))
                .andExpect(content().string(containsString(validationError)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void validRamiMessageReturns200FromAlternatePath() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH_ALTERNATE + "/message")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", testApiKey)
                        .content(validRamiMessage))
                .andExpect(content().string(""))
                .andExpect(status().isOk());
    }

}
