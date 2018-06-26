package fi.livi.rata.avoindata.server.localization;

import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrainCategoryCache extends LocalizationCache<Long, TrainCategory> {

    @Autowired
    protected TrainCategoryCache(final TrainCategoryRepository repository) {
        super(new TrainCategory(UNKNOWN_NAME), s -> repository.findById(s).orElse(null));
    }
}
