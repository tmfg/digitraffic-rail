package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class StationService {
    @Autowired
    private StationRepository stationRepository;

    @Transactional
    public void update(final Station[] stations) {
        stationRepository.deleteAllInBatch();
        stationRepository.persist(Arrays.asList(stations));
    }
}
