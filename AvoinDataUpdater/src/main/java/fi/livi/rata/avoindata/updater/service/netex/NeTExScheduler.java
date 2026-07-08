package fi.livi.rata.avoindata.updater.service.netex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the daily generation of both NeTEx Nordic packages (timetables and
 * compositions). Runs one hour before the GTFS generation to avoid resource
 * contention. Both services are injected as beans so their {@code @Transactional}
 * boundaries apply.
 */
@Component
public class NeTExScheduler {

    private static final Logger log = LoggerFactory.getLogger(NeTExScheduler.class);

    private final NeTExService neTExService;
    private final NeTExCompositionService neTExCompositionService;

    public NeTExScheduler(final NeTExService neTExService,
            final NeTExCompositionService neTExCompositionService) {
        this.neTExService = neTExService;
        this.neTExCompositionService = neTExCompositionService;
    }

    @Scheduled(cron = "${updater.netex.cron}", zone = "UTC")
    public void generateNeTExPackages() {
        log.info("method=generateNeTExPackages Starting scheduled NeTEx generation (timetables + compositions)");
        neTExService.generateNeTEx();
        neTExCompositionService.generateCompositions();
        log.info("method=generateNeTExPackages Finished scheduled NeTEx generation (timetables + compositions)");
    }
}
