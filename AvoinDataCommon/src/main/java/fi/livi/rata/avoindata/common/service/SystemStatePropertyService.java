package fi.livi.rata.avoindata.common.service;

import fi.livi.rata.avoindata.common.ESystemStateProperty;
import fi.livi.rata.avoindata.common.dao.SystemStatePropertyRepository;
import fi.livi.rata.avoindata.common.domain.common.SystemStateProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class SystemStatePropertyService {

    @Autowired
    private SystemStatePropertyRepository systemStatePropertyRepository;

    public Boolean getValueAsBoolean(ESystemStateProperty systemStateProperty, String defaultValue) {
        return Boolean.parseBoolean(getValue(systemStateProperty.name(), defaultValue));
    }

    public Boolean getValueAsBoolean(ESystemStateProperty systemStateProperty) {
        return Boolean.parseBoolean(getValue(systemStateProperty.name(), "false"));
    }

    public void setValue(ESystemStateProperty systemStateProperty, Boolean value) {
        setValue(systemStateProperty.name(), value.toString());
    }

    /**
     * Can return null if SystemStateProperty does not exist in the database. This class can not add rows while getting a value due to
     * database user right demands.
     * <p/>
     * Please add initial values to SystemStateProperties table via FlyWay and enum to fi.livi.rata.avoindata.common
     * .ESystemStateProperty
     */
    private SystemStateProperty getSystemStateProperty(String id, String defaultValue) {
        SystemStateProperty systemStateProperty = systemStatePropertyRepository.findById(id).orElse(null);

        return systemStateProperty;
    }


    private String getValue(String id, String defaultValue) {
        return getSystemStateProperty(id, defaultValue).value;
    }

    private void setValue(String id, String value) {
        final SystemStateProperty systemStateProperty = getSystemStateProperty(id, value);
        systemStateProperty.value = value;
        systemStatePropertyRepository.save(systemStateProperty);
    }
}
