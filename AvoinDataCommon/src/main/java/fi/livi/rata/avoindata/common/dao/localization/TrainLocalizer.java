package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.OptionalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrainLocalizer {
    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    public Train localize(Train train) {
        train.trainCategory = OptionalUtil.getName(trainCategoryRepository.findByIdCached(train.trainCategoryId));
        train.trainType = OptionalUtil.getName(trainTypeRepository.findByIdCached(train.trainTypeId));

        return train;
    }
}
