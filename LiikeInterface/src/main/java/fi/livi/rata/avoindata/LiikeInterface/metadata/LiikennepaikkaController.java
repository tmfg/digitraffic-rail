package fi.livi.rata.avoindata.LiikeInterface.metadata;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Liikennepaikka;
import fi.livi.rata.avoindata.LiikeInterface.metadata.service.LiikennepaikkaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class LiikennepaikkaController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private LiikennepaikkaService liikennepaikkaService;

    @RequestMapping("/avoin/stations")
    @ResponseBody
    public List<Liikennepaikka> getStations() {
        final List<Liikennepaikka> liikennepaikkas = liikennepaikkaService.getLiikennepaikkas();
        log.info("Retrieved station data");
        return liikennepaikkas;
    }
}
