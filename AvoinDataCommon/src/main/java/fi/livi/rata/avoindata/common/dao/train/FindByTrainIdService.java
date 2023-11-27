package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.train.Train;

// This service adds departureDate as an explicit argument in sql where, because Mysql does not work well with pair arguments in where
@Service
public class FindByTrainIdService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private AllTrainsRepository allTrainsRepository;

    private static List<TrainId> getSortedTrainIds(final Collection<TrainId> trainIds) {
        return trainIds.stream().sorted((l,r) -> l.compareTo(r)).collect(Collectors.toList());
    }

    private static Set<LocalDate> findUniqueDepartureDates(final Collection<TrainId> trainIds) {
        Set<LocalDate> departureDates = new HashSet<>();
        for (final TrainId trainId : trainIds) {
            departureDates.add(trainId.departureDate);
        }
        return departureDates;
    }

    public List<Train> findTrains(final Collection<TrainId> trainIds) {
        return this.trainRepository.findTrains(getSortedTrainIds(trainIds), findUniqueDepartureDates(trainIds));
    }

    public List<Train> findAllTrainsByIds(final Collection<TrainId> trainIds) {
        return this.allTrainsRepository.findTrains(getSortedTrainIds(trainIds), findUniqueDepartureDates(trainIds));
    }

    public List<Train> findTrainsIncludeDeleted(final Collection<TrainId> trainIds) {
        return this.trainRepository.findTrainsIncludeDeleted(getSortedTrainIds(trainIds), findUniqueDepartureDates(trainIds));
    }

    public List<Composition> findCompositions(final Collection<TrainId> trainIds) {
        return this.compositionRepository.findByIds(getSortedTrainIds(trainIds), findUniqueDepartureDates(trainIds));
    }
}
