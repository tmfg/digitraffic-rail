package fi.livi.rata.avoindata.updater.service.netex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the daily generation of the NeTEx Nordic dataset. Runs one hour
 * before the GTFS generation to avoid resource contention.
 */
@Component
public class NeTExScheduler {

    private static final Logger log = LoggerFactory.getLogger(NeTExScheduler.class);

    private final NeTExPackageService neTExPackageService;

    public NeTExScheduler(final NeTExPackageService neTExPackageService) {
        this.neTExPackageService = neTExPackageService;
    }

    @Scheduled(cron = "${updater.netex.cron:0 0 4 * * *}", zone = "UTC")
    public void generateNeTExPackages() {
        log.info("method=generateNeTExPackages Starting scheduled NeTEx dataset generation");
        neTExPackageService.generatePackage();
        log.info("method=generateNeTExPackages Finished scheduled NeTEx dataset generation");
    }
}
