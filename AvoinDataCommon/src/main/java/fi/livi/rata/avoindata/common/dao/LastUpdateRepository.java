package fi.livi.rata.avoindata.common.dao;

import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.domain.common.LastUpdate;

@Repository
public interface LastUpdateRepository extends CustomGeneralRepository<LastUpdate, String> {
}
