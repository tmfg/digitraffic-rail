package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

//import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenJourneyCompositionFilter;

import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.CompositionPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CompositionInitializerService extends AbstractDatabaseInitializer<JourneyComposition> {
    @Autowired
    private CompositionPersistService compositionPersistService;

    @Override
    public String getPrefix() {
        return "compositions";
    }

    @Override
    public AbstractPersistService<JourneyComposition> getPersistService() {
        return compositionPersistService;
    }

    @Override
    protected Class<JourneyComposition[]> getEntityCollectionClass() {
        return JourneyComposition[].class;
    }

}
