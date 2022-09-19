package fi.livi.rata.avoindata.updater.controllers;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;

import fi.livi.rata.avoindata.updater.service.rami.RamiValidationService;

@Controller
public class RamiIntegrationController {

    public static final String BASE_PATH = "/api/rami/incoming";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final RamiValidationService ramiValidationService;

    public RamiIntegrationController(final RamiValidationService ramiValidationService) {
        this.ramiValidationService = ramiValidationService;
    }

    @PostMapping(value = BASE_PATH + "/message",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity handleMessage(
            @RequestBody
            final JsonNode body) {
        final Set<ValidationMessage> errors = ramiValidationService.validateRamiMessage(body);
        if (errors.isEmpty()) {
            logger.info("Received valid RAMI message: {}", body);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().body(errors.toString());
        }
    }

    @PostMapping(value = BASE_PATH + "/situation",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity handleSituation(
            @RequestBody
            final JsonNode body) {
        final Set<ValidationMessage> errors = ramiValidationService.validateRamiSituation(body);
        if (errors.isEmpty()) {
            logger.info("Received valid RAMI situation: {}", body);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().body(errors.toString());
        }
    }

}
