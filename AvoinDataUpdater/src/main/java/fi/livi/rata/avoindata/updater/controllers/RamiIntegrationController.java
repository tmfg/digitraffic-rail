package fi.livi.rata.avoindata.updater.controllers;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

import fi.livi.rata.avoindata.updater.service.rami.RamiValidationService;

@Controller
@ConditionalOnProperty(prefix = "rami",
                       name = "enabled",
                       havingValue = "true")
public class RamiIntegrationController {

    public static final String BASE_PATH = "/api/v1/rami/incoming";
    public static final String BASE_PATH_ALTERNATE = "/api/rami/incoming";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final RamiValidationService ramiValidationService;

    private final String queueUrl;

    public RamiIntegrationController(final RamiValidationService ramiValidationService,
                                     @Value("${rami.sqs.url}")
                                     final String queueUrl) {
        this.ramiValidationService = ramiValidationService;
        this.queueUrl = queueUrl;
    }

    @PostMapping(value = { BASE_PATH + "/message", BASE_PATH_ALTERNATE + "/message" },
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity handleMessage(
            @RequestBody
            final JsonNode body) {
        final Set<ValidationMessage> errors = ramiValidationService.validateRamiMessage(body);
        if (errors.isEmpty()) {
            logger.info("Received valid RAMI message: {}", body);
            sendToQueue(body);
            return ResponseEntity.ok().build();
        } else {
            logger.warn("Received invalid RAMI message: {} with validation errors: {}", body, errors);
            return ResponseEntity.badRequest().body(errors.toString());
        }
    }

    private void sendToQueue(final JsonNode ramiMessage) {
        final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
        final SendMessageRequest request = new SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(ramiMessage.toString())
                .withDelaySeconds(5);
        sqs.sendMessage(request);
    }

}
