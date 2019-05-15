package fi.livi.rata.avoindata.LiikeInterface.routeset;

import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.routeset.Routeset;
import fi.livi.rata.avoindata.LiikeInterface.routeset.repository.RoutesetRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Controller
public class RoutesetController {
    private static final Logger log = LoggerFactory.getLogger(RoutesetController.class);

    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private ClassifiedTrainFilter classifiedTrainFilter;

    @RequestMapping(value = "/avoin/routesets", params = "date")
    @ResponseBody
    public Iterable<Routeset> getRoutesets(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("Requesting routeset data date " + date);

        final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));

        final ZonedDateTime start = date.atStartOfDay(ZoneId.of("Europe/Helsinki"));
        final ZonedDateTime end = date.plusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));

        List<Routeset> routesetList = routesetRepository.findByLahtopvm(date, start, end);
        log.info(String.format("Retrieved routeset data for %d messages in %s", routesetList.size(),
                Duration.between(now, ZonedDateTime.now())));

        return classifiedTrainFilter.filterClassifiedTrains(routesetList, s -> new JunapaivaPrimaryKey(s.trainNumber, s.departureDate == null ? LocalDate.now(ZoneId.of("Europe/Helsinki")) : s.departureDate));
    }

    @RequestMapping(value = "/avoin/routesets", params = "version")
    @ResponseBody
    public Iterable<Routeset> getRoutesets(@RequestParam final Long version) {
        log.info("Requesting routeset data version " + version);

        final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));

        List<Routeset> routesetList = routesetRepository.findByVersioGreaterThan(version);

        log.info(String.format("Retrieved %d routeset data for %s ms (version %d)", routesetList.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), version));

        return classifiedTrainFilter.filterClassifiedTrains(routesetList, s -> new JunapaivaPrimaryKey(s.trainNumber, s.departureDate == null ? LocalDate.now(ZoneId.of("Europe/Helsinki")) : s.departureDate));
    }

}

