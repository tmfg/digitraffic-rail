package fi.livi.rata.avoindata.updater.controller;

import static fi.livi.rata.avoindata.updater.config.RamiApiKeyValidationFilter.INVALID_API_KEY_ERROR;
import static fi.livi.rata.avoindata.updater.controllers.RamiIntegrationController.BASE_PATH;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import fi.livi.rata.avoindata.updater.BaseTest;

@AutoConfigureMockMvc
public class RamiIntegrationControllerTest extends BaseTest {

    @Autowired
    private MockMvc mvc;

    @Value("${rami.api-key}")
    private String testApiKey;

    private final String validRamiMessage = "{ \"headers\": { \"e2eId\": \"f8635d35-216a-4f1d-bd8b-2d994a4e0f72\", \"eventType\": \"ExternalScheduledMessage\", \"source\": \"mop-mms-ifmb-schedule-scheduledmsgmgr\", \"eventId\": \"123\", \"recordedAtTime\": \"2022-09-14T10:26:04.624Z\" }, \"payload\": { \"messageVersion\": 1, \"messageId\": \"SHM20211217103239796\", \"title\": \"Title message\", \"messageType\": \"SCHEDULED_MESSAGE\", \"operation\": \"INSERT\", \"startValidity\": \"2023-04-06T13:04:13.321Z\", \"endValidity\": \"2023-04-08T13:04:13.321Z\", \"creationDateTime\": \"2023-04-06T13:04:13.321Z\", \"messageContent\": \"liirum laarum\" } }";
    private final String invalidRamiMessage = "{ \"headers\": { \"eventType\": \"ExternalScheduledMessage\", \"source\": \"mop-mms-ifmb-schedule-scheduledmsgmgr\", \"eventId\": \"123\", \"recordedAtTime\": \"2022-09-14T10:26:04.624Z\" }, \"payload\": { \"messageVersion\": 1, \"messageId\": \"SHM20211217103239796\", \"title\": \"Title message\", \"messageType\": \"SCHEDULED_MESSAGE\", \"operation\": \"INSERT\", \"startValidity\": \"2023-04-06T13:04:13.321Z\", \"endValidity\": \"2023-04-08T13:04:13.321Z\", \"creationDateTime\": \"2023-04-06T13:04:13.321Z\", \"messageContent\": \"liirum laarum\" } }";

      private final String validationError = "e2eId: is missing";

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

}
