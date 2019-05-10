package fi.livi.rata.avoindata.updater.service.routeset;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesectionRepository;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.updater.service.VersionedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoutesetService extends VersionedService<Routeset> {
    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private RoutesectionRepository routesectionRepository;

    @Override
    @Transactional
    public void updateObjects(final List<Routeset> objects) {
        removeRoutesetsById(objects);
        routesetRepository.flush();

        routesetRepository.persist(objects);

        List<Routesection> routesections = new ArrayList<>();
        for (final Routeset routeset : objects) {
            routesections.addAll(routeset.routesections);
        }
        routesectionRepository.persist(routesections);

    }

    @Override
    public Long getMaxVersion() {
        return routesetRepository.getMaxVersion();
    }

    private void removeRoutesetsById(final List<Routeset> entities) {
        if (entities.isEmpty()) {
            return;
        }

        final List<Long> idsToRemove = Lists.transform(entities, s -> s.id);

        routesetRepository.removeById(idsToRemove);
    }

    @Transactional
    public void addEntities(final List<Routeset> routesets) {
      updateObjects(routesets);
    }
}
