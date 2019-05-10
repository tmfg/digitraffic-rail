package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.RoutesetPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoutesetInitializerService extends AbstractDatabaseInitializer<Routeset> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

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
        List<Routeset> updatedEntities = super.doUpdate();

        sendEntitiesToMqtt(updatedEntities);

        timeTableRowByRoutesetUpdateService.updateTrainByRouteset(updatedEntities);

        return updatedEntities;
    }

    private void sendEntitiesToMqtt(final List<Routeset> updatedEntities) {
        try {
            for (Routeset entity : updatedEntities) {

                mqttPublishService.publishEntity(
                        String.format("routesets/%s/%s", entity.trainId.departureDate, entity.trainId.trainNumber), entity, null);
            }
        } catch (Exception e) {
            log.error("Error publishing routesets to MQTT", e);
        }
    }
}
