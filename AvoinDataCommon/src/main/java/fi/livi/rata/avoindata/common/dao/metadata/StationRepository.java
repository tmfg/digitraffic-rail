package fi.livi.rata.avoindata.common.dao.metadata;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;

@Repository
public interface StationRepository extends CustomGeneralRepository<Station, Long> {
    Station findByShortCodeIgnoreCase(final String stationShortCode);
    Optional<Station> findByUicCode(final int uicCode);
}
