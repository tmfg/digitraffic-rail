package fi.livi.rata.avoindata.common.dao;

import fi.livi.rata.avoindata.common.domain.common.SystemStateProperty;
import org.springframework.stereotype.Repository;


@Repository
public interface SystemStatePropertyRepository extends CustomGeneralRepository<SystemStateProperty, String> {
}