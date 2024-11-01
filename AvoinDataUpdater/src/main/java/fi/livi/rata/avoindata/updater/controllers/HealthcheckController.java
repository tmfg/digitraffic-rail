package fi.livi.rata.avoindata.updater.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HealthcheckController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @RequestMapping("/healthcheck")
    @ResponseBody
    public boolean healthcheck() {
        log.debug("Healthcheck");
        return true;
    }
}
