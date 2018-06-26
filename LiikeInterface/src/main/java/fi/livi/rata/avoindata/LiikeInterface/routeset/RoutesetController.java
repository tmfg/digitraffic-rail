package fi.livi.rata.avoindata.LiikeInterface.routeset;

import com.google.common.base.Strings;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.routeset.Routeset;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.routeset.repository.RoutesetRepository;
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
import java.util.*;

@Controller
public class RoutesetController {
    private static final Logger log = LoggerFactory.getLogger(RoutesetController.class);

    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    private boolean firstFetch = true;

    @RequestMapping(value = "/avoin/routesets", params = "date")
    @ResponseBody
    public Collection<Routeset> getTrainRunningMessages(
            @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("Requesting routeset data date " + date);

        final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));

        final ZonedDateTime start = date.atStartOfDay(ZoneId.of("Europe/Helsinki"));
        final ZonedDateTime end = date.plusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));

        List<Routeset> routesetList = routesetRepository.findByLahtopvm(date, start, end);
        log.info(String.format("Retrieved routeset data for %d messages in %s", routesetList.size(),
                Duration.between(now, ZonedDateTime.now())));

        routesetList = filterClassifiedTrains(routesetList);

        return routesetList;
    }

    @RequestMapping(value = "/avoin/routesets", params = "version")
    @ResponseBody
    public Collection<Routeset> getTrainRunningMessages(@RequestParam(required = true) final Long version) {
        log.info("Requesting routeset data version " + version);

        final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));

        List<Routeset> routesetList = routesetRepository.findByVersioGreaterThan(version);
        routesetList = filterClassifiedTrains(routesetList);

        log.info(String.format("Retrieved %d routeset data for %s ms (version %d)", routesetList.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), version));

        firstFetch = false;
        return routesetList;

    }

    private List<Routeset> filterClassifiedTrains(final List<Routeset> routesetList) {
        Map<LocalDate, Set<JunapaivaPrimaryKey>> classifiedTrainsByDate = getClassifiedTrainsByDate(routesetList);


        List<Routeset> output = new ArrayList<>(routesetList.size());
        for (final Routeset routeset : routesetList) {
            if (!Strings.isNullOrEmpty(routeset.trainNumber) && routeset.departureDate != null) {
                JunapaivaPrimaryKey id = new JunapaivaPrimaryKey(routeset.trainNumber,routeset.departureDate);

                Set<JunapaivaPrimaryKey> classifiedTrainsForADay = classifiedTrainsByDate.get(routeset.departureDate);
                if (!classifiedTrainsForADay.contains(id)) {
                    output.add(routeset);
                } else {
                    log.info("Discarded classified routeset {} - {}", routeset.trainNumber, routeset.departureDate);
                }
            } else {
                output.add(routeset);
            }
        }

        return output;
    }

    private Map<LocalDate, Set<JunapaivaPrimaryKey>> getClassifiedTrainsByDate(final List<Routeset> routesetList) {
        Map<LocalDate, Set<JunapaivaPrimaryKey>> classifiedTrainsByDate = new HashMap<>();

        for (final Routeset routeset : routesetList) {
            if (!Strings.isNullOrEmpty(routeset.trainNumber) && routeset.departureDate != null && classifiedTrainsByDate.get(
                    routeset.departureDate) == null) {
                List<Junapaiva> classifiedTrains = junapaivaRepository.findClassifiedTrains(routeset.departureDate);
                Set<JunapaivaPrimaryKey> ids = new HashSet<JunapaivaPrimaryKey>(classifiedTrains.size());
                for (final Junapaiva classifiedTrain : classifiedTrains) {
                    ids.add(classifiedTrain.id);
                }
                classifiedTrainsByDate.put(routeset.departureDate, ids);
            }
        }
        return classifiedTrainsByDate;
    }


}

