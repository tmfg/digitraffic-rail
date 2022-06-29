package fi.livi.rata.avoindata.server.controller.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Hidden;

@Controller
@Hidden
public class HealthcheckController {
    @RequestMapping(method = RequestMethod.GET, path = "api")
    @ResponseBody
    public String healthcheck() {
        return "UP";
    }
}
