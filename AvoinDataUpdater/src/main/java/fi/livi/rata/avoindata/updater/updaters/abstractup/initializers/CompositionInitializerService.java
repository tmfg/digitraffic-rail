package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.CompositionPersistService;

import java.util.ArrayList;
import java.util.List;

@Service
public class CompositionInitializerService extends AbstractDatabaseInitializer<JourneyComposition> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CompositionPersistService compositionPersistService;

    @Autowired
    private MQTTPublishService mqttPublishService;

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

    @Override
    protected List<JourneyComposition> doUpdate() {
        List<JourneyComposition> updatedCompositions = super.doUpdate();

        mqttPublish(updatedCompositions);

        return updatedCompositions;
    }

    private void mqttPublish(List<JourneyComposition> updatedCompositions) {
        List<JourneySection> journeySections = Lists.transform(updatedCompositions, s -> s.journeySection);
        List<Composition> compositions = new ArrayList<>();
        for (JourneySection journeySection : journeySections) {
            compositions.add(journeySection.composition);
        }


        try {
            mqttPublishService.publish(s -> String
                    .format("compositions/%s/%s/%s/%s/%s", s.id.departureDate, s.id.trainNumber, s.trainCategory, s.trainType,
                            s.operator.operatorShortCode), compositions);
        } catch (Exception e) {
            log.error("Error publishing trains to MQTT", e);
        }
    }
}
