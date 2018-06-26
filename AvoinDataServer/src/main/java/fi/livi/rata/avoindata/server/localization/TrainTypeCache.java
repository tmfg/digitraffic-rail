package fi.livi.rata.avoindata.server.localization;

import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrainTypeCache extends LocalizationCache<Long, TrainType> {

    @Autowired
    protected TrainTypeCache(final TrainTypeRepository repository) {
        super(new TrainType(UNKNOWN_NAME), s -> repository.findById(s).orElse(null));
    }
}
