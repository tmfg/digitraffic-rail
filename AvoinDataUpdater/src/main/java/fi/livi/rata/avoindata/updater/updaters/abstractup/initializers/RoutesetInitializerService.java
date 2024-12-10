package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.RoutesetPersistService;

@Service
public class RoutesetInitializerService extends AbstractDatabaseInitializer<Routeset> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RoutesetPersistService routesetPersistService;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService;

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

    @Override
    protected List<Routeset> doUpdate() {
        final List<Routeset> updatedEntities = super.doUpdate();

        sendEntitiesToMqtt(updatedEntities);

        timeTableRowByRoutesetUpdateService.updateByRoutesets(updatedEntities);

        return updatedEntities;
    }

    private void sendEntitiesToMqtt(final List<Routeset> updatedEntities) {
        try {
            for (final Routeset entity : updatedEntities) {

                mqttPublishService.publishEntity(
                        String.format("routesets/%s/%s", entity.trainId.departureDate, entity.trainId.trainNumber), entity, null);
            }
        } catch (final Exception e) {
            log.error("Error publishing routesets to MQTT", e);
        }
    }
}
