package fi.livi.rata.avoindata.server.localization;

import fi.livi.rata.avoindata.common.dao.localization.PowerTypeRepository;
import fi.livi.rata.avoindata.common.domain.localization.PowerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PowerTypeCache extends LocalizationCache<String, PowerType> {

    @Autowired
    protected PowerTypeCache(final PowerTypeRepository repository) {
        super(new PowerType(UNKNOWN_NAME), repository::findByAbbreviation);
    }
}
