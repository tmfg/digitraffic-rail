package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

import java.util.List;

/**
 * Maps station metadata to NeTEx ScheduledStopPoints, RoutePoints, and DestinationDisplays.
 */
public class NeTExStopsService {

    private final NeTExIdGenerator idGenerator;

    public NeTExStopsService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Creates NeTEx stop data from station metadata.
     * Only includes stations with passengerTraffic=true.
     */
    public NeTExStopsData createStopsData(final List<Station> stations) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
