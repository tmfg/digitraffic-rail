package fi.livi.rata.avoindata.updater.service.siri;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.siri.vm.SiriVmGenerationService;

@Service
@ConditionalOnProperty(name = "updater.siri.vm.enabled", havingValue = "true")
public class SiriVmUpdatingService {

    private final SiriVmGenerationService siriVmGenerationService;

    public SiriVmUpdatingService(final SiriVmGenerationService siriVmGenerationService) {
        this.siriVmGenerationService = siriVmGenerationService;
    }

    @Scheduled(fixedRateString = "${updater.siri.vm.fixed-rate-ms:60000}")
    public void updateVm() {
        siriVmGenerationService.generate();
    }
}
