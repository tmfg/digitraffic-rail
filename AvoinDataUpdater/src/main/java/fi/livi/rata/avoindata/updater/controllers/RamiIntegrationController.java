package fi.livi.rata.avoindata.updater.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class RamiIntegrationController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostMapping("/rami/incoming")
    @ResponseBody
    public ResponseEntity handleIncoming(@RequestBody
                                  final JsonNode message) {
        logger.info("Received RAMI message: {}", message);
        return ResponseEntity.ok().build();
    }

}
