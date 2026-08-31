package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

/** In-memory {@link StationNameLookup} indexing {@code Station.shortCode -> Station.name}. */
public class InMemoryStationNameLookup implements StationNameLookup {

    private final Map<String, String> shortCodeToName;

    public InMemoryStationNameLookup(final List<Station> stations) {
        this.shortCodeToName = stations.stream()
                .filter(s -> s.name != null)
                .collect(Collectors.toMap(s -> s.shortCode, s -> s.name, (a, b) -> a));
    }

    @Override
    public Optional<String> nameFor(final String stationShortCode) {
        return Optional.ofNullable(shortCodeToName.get(stationShortCode));
    }
}
