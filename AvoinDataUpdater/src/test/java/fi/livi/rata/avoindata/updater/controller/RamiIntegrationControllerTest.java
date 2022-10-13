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

    private final String validRamiMessage = "{\"headers\":{\"e2eId\":\"f8635d35-216a-4f1d-bd8b-2d994a4e0f72\",\"eventType\":\"ExternalScheduledMessage\",\"source\":\"mop-mms-ifmb-schedule-scheduledmsgmgr\",\"organisation\":\"RAMI\",\"recordedAtTime\":\"2022-09-14T10:26:04.624Z\"},\"payload\":{\"operation\":\"NEW\",\"messageId\":\"SHM20220914102645800\",\"messageVersion\":1,\"title\":\"RIPA-testitiedote\",\"startValidity\":\"2022-09-13T21:00:00Z\",\"endValidity\":\"2022-09-14T20:59:59Z\",\"externalPoints\":[{\"id\":\"TNPNTS00000000007039\",\"nameLong\":\"HELSINKI\",\"nameShort\":\"HKI\"}],\"messageContent\":[{\"language\":\"fi_FI\",\"text\":\"Tämä on testitiedote. Testaamme tiedotteiden liikkumista ulkoisiin järjestelmiin, kuten web- ja mobiilisovelluksiin.\"}],\"situation\":[]}}";
    private final String invalidRamiMessage = "{\"headers\":{\"e2eIde\":\"f8635d35-216a-4f1d-bd8b-2d994a4e0f72\",\"eventType\":\"ExternalScheduledMessage\",\"source\":\"mop-mms-ifmb-schedule-scheduledmsgmgr\",\"organisation\":\"RAMI\",\"recordedAtTime\":\"2022-09-14T10:26:04.624Z\"},\"payload\":{\"operation\":\"NEW\",\"messageId\":\"SHM20220914102645800\",\"messageVersion\":1,\"title\":\"RIPA-testitiedote\",\"startValidity\":\"2022-09-13T21:00:00Z\",\"endValidity\":\"2022-09-14T20:59:59Z\",\"externalPoints\":[{\"id\":\"TNPNTS00000000007039\",\"nameLong\":\"HELSINKI\",\"nameShort\":\"HKI\"}],\"messageContent\":[{\"language\":\"fi_FI\",\"text\":\"Tämä on testitiedote. Testaamme tiedotteiden liikkumista ulkoisiin järjestelmiin, kuten web- ja mobiilisovelluksiin.\"}],\"situation\":[]}}";

    private final String validRamiSituation = "{ \"headers\": { \"e2eId\": \"439e4b09-c0ba-11ec-a9e2-7fcdd76df0c3\", \"organisation\": \"MOOVA\", \"source\": \"ihbinm-integratedmobilityexception-api-exceptionexternal\", \"eventType\": \"updateSituation\", \"eventId\": \"c9472941-fcf4-11e7-8290-0242ac110005\", \"recordedAtTime\": \"2022-05-16T13:26:05.038Z\" }, \"payload\": { \"countryRef\": \"ITA\", \"participantRef\": \"MOP\", \"situationNumber\": \"439e4b09-c0ba-11ec-a9e2-7fcdd76df0c3\", \"version\": \"1\", \"verification\": \"unknown\", \"progress\": \"open\", \"qualityIndex\": \"probablyReliable\", \"reality\": \"unconfirmed\", \"likelihood\": \"improbable\", \"validityPeriod\": [ { \"start\": \"2022-04-20T14:57:40Z\", \"end\": \"2022-04-21T14:57:40Z\" } ], \"publicationWindow\": { \"start\": \"2022-04-20T14:57:40Z\", \"end\": \"2022-04-21T14:57:40Z\" }, \"reasonType\": \"miscellaneousReason\", \"miscellaneousReason\": \"unknown\", \"personnelReason\": \"unknown\", \"equipmentReason\": \"unknown\", \"environmentReason\": \"unknown\", \"publicEventReason\": \"tennisTournament\", \"reasonName\": \"\", \"miscellaneousSubReason\": \"logisticProblems\", \"severity\": \"normal\", \"audience\": \"public\", \"scopeType\": \"network\", \"description\": \"fer\", \"creationTime\": \"2022-04-20T14:57:57.257Z\", \"affects\": { \"allOperators\": false, \"networks\": [ { \"networkRef\": \"TSPNET00000001000021\", \"networkName\": \"Network FTA\" } ] }, \"extensions\": { \"native\": { \"updateTime\": \"2022-05-16T13:26:00.842Z\", \"situationType\": \"infomobility\" } } } }";
    private final String invalidRamiSituation = "{ \"headers\": { \"e2eIde\": \"439e4b09-c0ba-11ec-a9e2-7fcdd76df0c3\", \"organisation\": \"MOOVA\", \"source\": \"ihbinm-integratedmobilityexception-api-exceptionexternal\", \"eventType\": \"updateSituation\", \"recordedAtTime\": \"2022-05-16T13:26:05.038Z\" }, \"payload\": { \"countryRef\": \"ITA\", \"participantRef\": \"MOP\", \"situationNumber\": \"439e4b09-c0ba-11ec-a9e2-7fcdd76df0c3\", \"version\": \"1\", \"verification\": null, \"progress\": \"open\", \"qualityIndex\": null, \"reality\": null, \"likelihood\": null, \"validityPeriod\": [ { \"start\": \"2022-04-20T14:57:40Z\", \"end\": null } ], \"repetitions\": null, \"publicationWindow\": null, \"reasonType\": \"miscellaneousReason\", \"miscellaneousReason\": null, \"personnelReason\": null, \"equipmentReason\": null, \"environmentReason\": null, \"publicEventReason\": null, \"reasonName\": null, \"miscellaneousSubReason\": null, \"personnelSubReason\": null, \"equipmentSubReason\": null, \"environmentSubReason\": null, \"severity\": \"normal\", \"priority\": null, \"sensitivity\": null, \"audience\": \"public\", \"reportType\": null, \"scopeType\": \"network\", \"planned\": false, \"language\": null, \"summary\": null, \"description\": \"fer\", \"detail\": null, \"advice\": null, \"internal\": null, \"creationTime\": \"2022-04-20T14:57:57.257Z\", \"references\": null, \"source\": null, \"keywords\": null, \"affects\": { \"allOperators\": false, \"operators\": null, \"stopPoints\": null, \"networks\": [ { \"operators\": null, \"networkRef\": \"TSPNET00000001000021\", \"networkName\": \"Network FTA\", \"routesAffected\": null, \"vehicleModes\": null, \"allLines\": false, \"selectedRoutes\": false, \"lines\": null } ], \"lines\": null, \"vehicleJourneys\": null, \"stopPlaces\": null, \"places\": null }, \"consequences\": null, \"publishingActions\": null, \"extensions\": { \"custom\": null, \"native\": { \"updateTime\": \"2022-05-16T13:26:00.842Z\", \"descriptionDelete\": null, \"situationType\": \"infomobility\", \"expectedStart\": null, \"expectedEnd\": null } }, \"relatedItems\": null } }";

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

    @Test
    public void validRamiSituationReturns200() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH + "/situation")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", testApiKey)
                        .content(validRamiSituation))
                .andExpect(content().string(""))
                .andExpect(status().isOk());
    }

    @Test
    public void invalidRamiSituationIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders
                        .post(BASE_PATH + "/situation")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .header("API-KEY", testApiKey)
                        .content(invalidRamiSituation))
                .andExpect(content().string(containsString(validationError)))
                .andExpect(status().isBadRequest());
    }

}
