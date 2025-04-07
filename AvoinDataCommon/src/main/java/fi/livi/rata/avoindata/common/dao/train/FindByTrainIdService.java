package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
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
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private AllTrainsRepository allTrainsRepository;

    private static List<TrainId> getSortedTrainIds(final Collection<TrainId> trainIds) {
        return trainIds.stream().sorted().toList();
    }

    private static Set<LocalDate> findUniqueDepartureDates(final Collection<TrainId> trainIds) {
        return trainIds.stream().map(trainId -> trainId.departureDate).collect(Collectors.toCollection(HashSet::new));
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

    public void removeByTrainId(final List<TrainId> trainIds) {
        trainRepository.removeByTrainId(getSortedTrainIds(trainIds), findUniqueDepartureDates(trainIds));
    }

    public List<AllTrainsRepository.FindByVersionQueryResult> getTrainsGreaterThanVersionRecursive(final Long version, final int maxRows) {
        final List<AllTrainsRepository.FindByVersionQueryResult> results =
                allTrainsRepository.findByVersionGreaterThanRawSql(version, maxRows);

        if (results.size() == maxRows) {
            // already sorted by version
            // do not return results containing rows over maxRows that consist of only a single version, fetch again for next version in these cases
            // partial versions should not be returned
            // single versions containing over maxRows are historical anomalies that can be skipped
            if (results.getFirst().getVersion().equals(results.getLast().getVersion())) {
                log.warn("Version {} contains over {} rows, skipping and fetching for next version", version, maxRows);
                return getTrainsGreaterThanVersionRecursive(results.getFirst().getVersion(), maxRows);
            }
            // filter out last version from results of over
            // maxRows to avoid returning partial version sets
            // it is assumed here that the results are in ASC order by version
            return results.stream().filter(result -> !result.getVersion().equals(results.getLast().getVersion())).toList();
        }

        return results;
    }

}
