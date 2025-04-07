package fi.livi.rata.avoindata.common.dao.composition;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.train.FindByTrainIdService;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;

@Service
public class FindCompositionsByVersionService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private FindByTrainIdService findByTrainIdService;

    public List<Composition> findByVersionGreaterThan(final Long version, final int maxRows) {
        final List<TrainIdWithVersion> idsWithVersion =
                findIdsByVersionGreaterThanRecursive(version != null ? version : compositionRepository.getMaxVersion() - 1, maxRows);

        if (!idsWithVersion.isEmpty()) {
            final List<TrainId> trainIds = idsWithVersion.stream()
                    .map(trainIdWithVersion -> new TrainId(trainIdWithVersion.getTrainNumber(), trainIdWithVersion.getDepartureDate())).toList();
            return findByTrainIdService.findCompositions(trainIds);
        } else {
            return new ArrayList<>();
        }
    }

    private List<TrainIdWithVersion> findIdsByVersionGreaterThanRecursive(final long version, final int maxRows) {

        final List<TrainIdWithVersion> results = compositionRepository.findIdsByVersionGreaterThan(version, PageRequest.of(0, maxRows + 1));

        if (results.size() > maxRows) {
            // results is already sorted by version, so we can just take the first and last
            // do not return results containing rows over maxRows that consist of only a single version, fetch again for next version in these cases
            // partial versions should not be returned
            // single versions containing over maxRows are historical anomalies but return it anyway
            if (results.getFirst().getVersion() == results.getLast().getVersion()) {
                log.error(
                        "method=findIdsByVersionGreaterThanRecursive with version={} firstVersion={} contains count={} rows that is over {} rows. " +
                        "Returning them, but this should only happen when fetching historical compositions.",
                        version, results.getFirst().getVersion(), results.size(), maxRows);
                return results;
            }
            // filter out last version from results of over
            // maxRows to avoid returning partial version sets
            // it is assumed here that the results are in ASC order by version
            log.warn(
                    "method=findIdsByVersionGreaterThanRecursive with version={} firstVersion={} lastVersion={} contains count={} rows that is over {} rows. " +
                    "skipping last version",
                    version, results.getFirst().getVersion(), results.getLast().getVersion(), results.size(), maxRows);
            return results.stream().filter(result -> result.getVersion() != results.getLast().getVersion()).toList();
        }
        return results;
    }
}
