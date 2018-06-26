package fi.livi.rata.avoindata.LiikeInterface.jupaennuste;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaEnnuste;
import fi.livi.rata.avoindata.LiikeInterface.jupaennuste.repository.JupaennusteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;

@Controller
public class JupaennusteController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JupaennusteRepository jupaennusteRepository;

    @RequestMapping("/avoin/forecasts")
    @ResponseBody
    public Collection<JupaEnnuste> getEntities(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
            @RequestParam(required = false) final Long version) {

        log.info("Requesting jupaennuste data: from " + date + " with version " + version);

        final ZonedDateTime now = ZonedDateTime.now();
        final long currentVersion = version != null ? version : -1L;
        final Collection<JupaEnnuste> entities;

        if (date != null) {
            entities = new HashSet<>(jupaennusteRepository.findByLahtoPvm(date));
        } else {
            final LocalDate nowLocalDate = LocalDate.now(ZoneId.of("Europe/Helsinki"));
            entities = new HashSet<>(jupaennusteRepository.findByVersion(currentVersion, nowLocalDate.minusDays(2)));
        }

        log.info(String.format("Retrieved data for %d jupaennuste in %s ms (version %d)", entities.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), currentVersion));

        return entities;
    }
}
