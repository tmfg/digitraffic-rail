package fi.livi.rata.avoindata.server.factory;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;

@Component
public class TrainTypeFactory {
    @Autowired
    private TrainTypeRepository trainTypeRepository;

    public TrainType create(TrainCategory trainCategory) {
        TrainType trainType = new TrainType();
        trainType.id = 1L;
        trainType.name = "testTrainType";
        trainType.trainCategory = trainCategory;

        return trainTypeRepository.save(trainType);
    }
}
