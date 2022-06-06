package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PlatformData {

    public Map<String, List<InfraApiPlatform>> platformsByStation;
    public Map<String, Set<String>> validTracksByStation;

    public PlatformData(final Map<String, List<InfraApiPlatform>> platformsByStation) {
        this.platformsByStation = platformsByStation;

        this.validTracksByStation = platformsByStation.keySet().stream()
                .collect(Collectors.toMap(
                        stationShortCode -> stationShortCode,
                        stationShortCode -> platformsByStation.get(stationShortCode)
                                .stream()
                                .map(infraApiPlatform -> infraApiPlatform.commercialTrack)
                                .collect(Collectors.toSet()))
                );
    }

    public Optional<InfraApiPlatform> getStationPlatform(String stationShortCode, String track) {
        return platformsByStation.getOrDefault(stationShortCode, Collections.emptyList()).stream()
                .filter(platform -> platform.commercialTrack.equals(track))
                .findAny();
    }

    public boolean isValidTrack(String stationShortCode, String track) {
        return validTracksByStation.getOrDefault(stationShortCode, Collections.emptySet()).contains(track);
    }
}
