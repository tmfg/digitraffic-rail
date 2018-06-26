package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.localization.Localizations;
import fi.livi.rata.avoindata.updater.service.LocalizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LocalizationUpdater extends AEntityUpdater<Localizations> {
    @Autowired
    private LocalizationService localizationService;

    @Override
    @Scheduled(fixedDelay = 1000 * 60 * 30L)
    protected void update() {
        this.doUpdate("localizations", localizationService::updateLocalizations, Localizations.class);
    }
}
