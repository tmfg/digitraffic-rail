package fi.livi.rata.avoindata.updater.service.gtfs.observability;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;

/**
 * What {@link GTFSService} records while orchestrating a run's feeds. Reading the accumulated run
 * outcome is not part of this: only the owner that created the accumulator emits the wide event.
 */
public interface FeedMetricsSink {

    void recordNodeMap(InfraApiMapResult<?> nodeMap);

    void recordFeedAttempt(String feedName);

    void recordFeedPublished(String feedName);

    void recordFeedFailed(String feedName);

    void markError(Throwable throwable);
}
