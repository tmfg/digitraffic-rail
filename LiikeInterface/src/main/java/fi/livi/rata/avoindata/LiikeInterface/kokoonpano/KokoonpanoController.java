package fi.livi.rata.avoindata.LiikeInterface.kokoonpano;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Kokoonpano;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.kokoonpano.repository.KokoonpanoRepository;
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
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;

@Controller
public class KokoonpanoController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private KokoonpanoRepository kokoonpanoRepository;

    @RequestMapping("/avoin/compositions")
    @ResponseBody
    @JsonView(JunapaivaController.class)
    public Collection<Kokoonpano> getKokoonpanos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
            @RequestParam(required = false) final Long version) {

        log.info("Requesting composition data: from " + date + " with version " + version);

        final ZonedDateTime now = ZonedDateTime.now();
        final long currentVersion = version != null ? version : -1L;
        final Collection<Kokoonpano> kokoonpanos;

        if (date != null) {
            kokoonpanos = new HashSet<>(kokoonpanoRepository.findByLahtoPvm(date));
        } else {
            kokoonpanos = new HashSet<>(kokoonpanoRepository.findByVersion(currentVersion, LocalDate.now().minusDays(3)));
        }

        log.info(String.format("Retrieved composition data for %d compositions in %s ms (version %d)", kokoonpanos.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), currentVersion));

        return kokoonpanos;
    }
}
