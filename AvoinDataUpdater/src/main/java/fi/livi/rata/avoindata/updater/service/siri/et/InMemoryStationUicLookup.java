package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

public class InMemoryStationUicLookup implements StationUicLookup {

    private final Map<String, Integer> shortCodeToUic;

    public InMemoryStationUicLookup(final List<Station> stations) {
        this.shortCodeToUic = stations.stream()
                .collect(Collectors.toMap(s -> s.shortCode, s -> s.uicCode, (a, b) -> a));
    }

    @Override
    public OptionalInt uicFor(final String stationShortCode) {
        final Integer uic = shortCodeToUic.get(stationShortCode);
        return uic != null ? OptionalInt.of(uic) : OptionalInt.empty();
    }
}
