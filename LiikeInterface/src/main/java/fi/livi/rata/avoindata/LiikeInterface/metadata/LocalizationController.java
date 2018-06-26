package fi.livi.rata.avoindata.LiikeInterface.metadata;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.localization.Localizations;
import fi.livi.rata.avoindata.LiikeInterface.metadata.service.LocalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LocalizationController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private LocalizationService service;

    @RequestMapping("/avoin/localizations")
    @ResponseBody
    public Localizations getLocalizations() {
        log.info("Retrieving metadata");
        final Localizations localizations = service.getLocalizations();
        log.info("Metadata retrieved");
        return localizations;
    }
}
