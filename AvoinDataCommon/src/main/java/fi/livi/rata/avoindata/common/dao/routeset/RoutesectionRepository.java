package fi.livi.rata.avoindata.common.dao.routeset;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;

@Repository
public interface RoutesectionRepository extends CustomGeneralRepository<Routesection, Long> {

}
