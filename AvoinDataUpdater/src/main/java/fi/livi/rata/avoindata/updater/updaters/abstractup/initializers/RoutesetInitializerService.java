package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.RoutesetPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoutesetInitializerService extends AbstractDatabaseInitializer<Routeset> {
    @Autowired
    private RoutesetPersistService routesetPersistService;

    @Override
    public String getPrefix() {
        return "routesets";
    }

    @Override
    public AbstractPersistService<Routeset> getPersistService() {
        return routesetPersistService;
    }

    @Override
    protected Class<Routeset[]> getEntityCollectionClass() {
        return Routeset[].class;
    }



}
