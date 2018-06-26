package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.localization.PowerTypeRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.localization.Localizations;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class LocalizationService {

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @Autowired
    private PowerTypeRepository powerTypeRepository;

    @Transactional
    public void updateLocalizations(final Localizations localizations) {
        clearLocalizations();

        initializeTrainCategories(localizations);
        trainTypeRepository.persist(localizations.trainTypes);
        powerTypeRepository.persist(localizations.powerTypes);
    }

    private void initializeTrainCategories(final Localizations localizations) {
        //remove duplicates
        Map<Long,TrainCategory> trainCategoryMap = new HashMap<>(); for (final TrainType trainType : localizations.trainTypes) {
            trainCategoryMap.put(trainType.trainCategory.id, trainType.trainCategory);
        }
        for (final TrainType trainType : localizations.trainTypes) {
            trainType.trainCategory = trainCategoryMap.get(trainType.trainCategory.id);
        }
        trainCategoryRepository.persist(trainCategoryMap.values());
    }

    public void clearLocalizations() {
        trainTypeRepository.deleteAllInBatch();
        trainCategoryRepository.deleteAllInBatch();
        powerTypeRepository.deleteAllInBatch();
    }
}
