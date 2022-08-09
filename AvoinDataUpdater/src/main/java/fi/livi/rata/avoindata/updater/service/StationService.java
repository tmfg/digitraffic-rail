package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
public class StationService {
    @Autowired
    private StationRepository stationRepository;

    @Transactional
    public void update(final Station[] stations) {
        for (Station station : stations) {
            if (station.shortCode.equals("PTR")) {
                station.latitude = BigDecimal.valueOf(59.957131);
                station.longitude = BigDecimal.valueOf(30.356308);
            } else if (station.shortCode.equals("PTL")) {
                station.latitude = BigDecimal.valueOf(59.9311424);
                station.longitude = BigDecimal.valueOf(30.443925);
            } else if (station.shortCode.equals("MVA")) {
                station.latitude = BigDecimal.valueOf(55.777111);
                station.longitude = BigDecimal.valueOf(37.655278);
            } else if (station.shortCode.equals("TVE")) {
                station.latitude = BigDecimal.valueOf(56.835200);
                station.longitude = BigDecimal.valueOf(35.892800);
            }
        }

        stationRepository.deleteAllInBatch();
        stationRepository.persist(Arrays.asList(stations));
    }
}
