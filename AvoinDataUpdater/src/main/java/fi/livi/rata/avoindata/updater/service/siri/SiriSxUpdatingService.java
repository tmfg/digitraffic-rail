package fi.livi.rata.avoindata.updater.service.siri;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.siri.sx.SiriSxGenerationService;

@Service
@ConditionalOnProperty(name = "updater.siri.sx.enabled", havingValue = "true")
public class SiriSxUpdatingService {

    private final SiriSxGenerationService siriSxGenerationService;

    public SiriSxUpdatingService(final SiriSxGenerationService siriSxGenerationService) {
        this.siriSxGenerationService = siriSxGenerationService;
    }

    @Scheduled(fixedRateString = "${updater.siri.sx.fixed-rate-ms:60000}")
    public void updateSx() {
        siriSxGenerationService.generate();
    }
}
