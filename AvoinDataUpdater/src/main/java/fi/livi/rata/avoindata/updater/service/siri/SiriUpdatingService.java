package fi.livi.rata.avoindata.updater.service.siri;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.siri.et.SiriEtGenerationService;

@Service
@ConditionalOnProperty(name = "updater.siri.et.enabled", havingValue = "true")
public class SiriUpdatingService {

    private final SiriEtGenerationService siriEtGenerationService;

    public SiriUpdatingService(final SiriEtGenerationService siriEtGenerationService) {
        this.siriEtGenerationService = siriEtGenerationService;
    }

    @Scheduled(fixedRateString = "${updater.siri.et.fixed-rate-ms:60000}")
    public void updateEt() {
        siriEtGenerationService.generate();
    }
}
