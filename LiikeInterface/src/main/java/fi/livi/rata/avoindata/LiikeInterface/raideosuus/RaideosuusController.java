package fi.livi.rata.avoindata.LiikeInterface.raideosuus;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Raideosuus;
import fi.livi.rata.avoindata.LiikeInterface.metadata.service.InfraService;

@Controller
public class RaideosuusController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RaideosuusRepository raideosuusRepository;

    @Autowired
    private InfraService infraService;

    @RequestMapping(value = "/avoin/tracksections")
    @ResponseBody
    public Collection<Raideosuus> getNewest() {
        final ZonedDateTime now = ZonedDateTime.now();

        Collection<Raideosuus> items = raideosuusRepository.findNewest(infraService.getCurrentInfra().id);
        log.info(String.format("Retrieved Raideosuus %d messages in %s ms", items.size(), Duration.between(now, ZonedDateTime.now())
                .toMillis()));

        return items;
    }
}
