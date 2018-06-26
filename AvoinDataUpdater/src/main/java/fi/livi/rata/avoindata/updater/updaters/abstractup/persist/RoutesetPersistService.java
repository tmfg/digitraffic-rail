package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import fi.livi.rata.avoindata.common.dao.routeset.RoutesectionRepository;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenRoutesetFilter;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoutesetPersistService extends AbstractPersistService<Routeset> {
    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private RoutesectionRepository routesectionRepository;

    @Autowired
    private RecentlySeenRoutesetFilter recentlySeenRoutesetFilter;

    @Override
    @Transactional
    public List<Routeset> updateEntities(final List<Routeset> entities) {
        final List<Routeset> filteredEntities = recentlySeenRoutesetFilter.filter(entities);

        removeTrainRunningMessagesById(filteredEntities);
        routesetRepository.flush();

        addEntities(filteredEntities);

        return filteredEntities;
    }


    public Long getMaxVersion() {
        return routesetRepository.getMaxVersion();
    }

    private void removeTrainRunningMessagesById(final List<Routeset> entities) {
        if (entities.isEmpty()) {
            return;
        }

        List<Long> idsToRemove = new ArrayList<>(entities.size());
        for (final Routeset entity : entities) {
            idsToRemove.add(entity.id);
        }
        routesetRepository.removeById(idsToRemove);
    }

    @Override
    public void clearEntities() {
        routesetRepository.deleteAllInBatch();
    }

    @Transactional
    public void addEntities(final List<Routeset> entities) {
        routesetRepository.persist(entities);

        List<Routesection> routesections = new ArrayList<>();
        for (final Routeset routeset : entities) {
            routesections.addAll(routeset.routesections);
        }
        routesectionRepository.persist(routesections);
    }


}
