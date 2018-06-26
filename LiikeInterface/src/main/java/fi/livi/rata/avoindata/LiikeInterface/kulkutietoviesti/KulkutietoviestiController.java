package fi.livi.rata.avoindata.LiikeInterface.kulkutietoviesti;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Kulkutietoviesti;
import fi.livi.rata.avoindata.LiikeInterface.kulkutietoviesti.repository.KulkutietoviestiRepository;
import fi.livi.rata.avoindata.LiikeInterface.services.ClassifiedTrainFilter;
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
import java.util.List;
import java.util.function.Function;

@Controller
public class KulkutietoviestiController {
    private static final Logger log = LoggerFactory.getLogger(KulkutietoviestiController.class);
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 15;
    public static final int TIMESTAMP_LONG_TRESHOLD_DAYS = 1;

    @Autowired
    private KulkutietoviestiRepository kulkutietoviestiRepository;

    @Autowired
    private ClassifiedTrainFilter classifiedTrainFilter;

    private boolean firstFetch = true;

    private static Function<Kulkutietoviesti, JunapaivaPrimaryKey> primaryKeyFunction = k -> {
        final LocalDate lahtopvm = k.lahtopvm != null ? k.lahtopvm : k.tapahtumaV;
        return new JunapaivaPrimaryKey(k.junanumero, lahtopvm);
    };

    @RequestMapping(value = "/avoin/trainrunningmessages", params = "date")
    @ResponseBody
    public Collection<Kulkutietoviesti> getTrainRunningMessages(
            @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("Requesting trainrunningmessage data date " + date);

        final ZonedDateTime now = ZonedDateTime.now();

        List<Kulkutietoviesti> kulkutietoviestiCollection = kulkutietoviestiRepository.findByLahtopvm(date);
        log.info(String.format("Retrieved trainrunningmessage data for %d messages in %s", kulkutietoviestiCollection.size(),
                Duration.between(now, ZonedDateTime.now())));

        kulkutietoviestiCollection = filterClassifiedTrains(kulkutietoviestiCollection);

        return kulkutietoviestiCollection;
    }

    @RequestMapping(value = "/avoin/trainrunningmessages", params = "version")
    @ResponseBody
    public Collection<Kulkutietoviesti> getTrainRunningMessages(@RequestParam(required = true) final Long version) {
        log.info("Requesting trainrunningmessage data version " + version);

        final ZonedDateTime now = ZonedDateTime.now();

        final ZonedDateTime minimumTapahtumaPvm;
        if (firstFetch) {
            minimumTapahtumaPvm = now.minusDays(TIMESTAMP_LONG_TRESHOLD_DAYS);
        } else {
            minimumTapahtumaPvm = now.minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES);
        }

        List<Kulkutietoviesti> kulkutietoviestiCollection = kulkutietoviestiRepository.findByVersioGreaterThan(version,
                minimumTapahtumaPvm);
        kulkutietoviestiCollection = filterClassifiedTrains(kulkutietoviestiCollection);

        log.info(String.format("Retrieved %d trainrunningmessage data for %s ms (version %d)", kulkutietoviestiCollection.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), version));

        firstFetch = false;
        return kulkutietoviestiCollection;

    }

    private List<Kulkutietoviesti> filterClassifiedTrains(List<Kulkutietoviesti> kulkutietoviestiCollection) {
        kulkutietoviestiCollection = Lists.newArrayList(classifiedTrainFilter.filterClassifiedTrains(kulkutietoviestiCollection, primaryKeyFunction));
        return kulkutietoviestiCollection;
    }
}

