package fi.livi.rata.avoindata.common.dao.localization;

import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.utils.OptionalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CompositionLocalizer {
    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Autowired
    private PowerTypeRepository powerTypeRepository;

    public Composition localize(Composition composition) {
        composition.trainCategory = OptionalUtil.getName(trainCategoryRepository.findByIdCached(composition.trainCategoryId));
        composition.trainType = OptionalUtil.getName(trainTypeRepository.findByIdCached(composition.trainTypeId));

        for (final JourneySection journeySection : composition.journeySections) {
            for (final Locomotive locomotive : journeySection.locomotives) {
                locomotive.powerType = OptionalUtil.getName(powerTypeRepository.findByAbbreviationCached(locomotive.powerTypeAbbreviation));
            }
        }

        return composition;
    }
}
