package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;

// This service adds departureDate as an explicit argument in sql where, because Mysql does not work well with pair arguments in where
@Service
public class FindByTrainIdService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRepository trainRepository;

    @Transactional
    public List<Train> findTrains(final Collection<TrainId> trainIds) {
        Set<LocalDate> departureDates = new HashSet<>();
        for (final TrainId trainId : trainIds) {
            departureDates.add(trainId.departureDate);
        }

        return this.trainRepository.findTrains(trainIds, departureDates);
    }

    @Transactional
    public List<Train> findTrainsIncludeDeleted(final Collection<TrainId> trainIds) {
        Set<LocalDate> departureDates = new HashSet<>();
        for (final TrainId trainId : trainIds) {
            departureDates.add(trainId.departureDate);
        }

        return this.trainRepository.findTrainsIncludeDeleted(trainIds, departureDates);
    }
}
