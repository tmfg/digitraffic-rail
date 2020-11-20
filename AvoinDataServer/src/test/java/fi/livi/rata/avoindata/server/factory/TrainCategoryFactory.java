package fi.livi.rata.avoindata.server.factory;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;

@Component
public class TrainCategoryFactory {
    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    public TrainCategory create(long id, String name) {
        TrainCategory trainCategory = new TrainCategory();
        trainCategory.id = id;
        trainCategory.name = name;

        return trainCategoryRepository.save(trainCategory);
    }
}
