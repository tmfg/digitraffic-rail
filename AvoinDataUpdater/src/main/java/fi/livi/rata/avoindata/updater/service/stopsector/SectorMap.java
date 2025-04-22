package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.CsvUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SectorMap {
    private static class WagonCountToSectorMap extends HashMap<Integer, String> {}
    private static class TypeMap extends HashMap<String, WagonCountToSectorMap> {}
    private static class DirectionMap extends HashMap<Boolean, TypeMap> {}
    private static class TrackMap extends HashMap<String, DirectionMap> {}

    private final Map<String, TrackMap> stationMap = new HashMap<>();

    private static final Logger log = LoggerFactory.getLogger(SectorMap.class);

    public record StopSector(String station, String track, String type, boolean isSouth, int wagonCount, String sector) {
    }

    public void initialize(final String fileName) {
        final var sectors = CsvUtil.readFile(fileName, t -> {
            final String station = t[1];
            final String track = t[2];
            final String trainType = t[3];
            final String locomotiveType = t[4];
            final String directionString = t[5];
            final String wagonCountString = t[7];
            final String sector = t[8];

            final String type = trainType.isEmpty() ? locomotiveType : trainType;
            final boolean isSouth = StringUtils.equals(directionString, "SOUTH");
            final int wagonCount = Integer.parseInt(wagonCountString);

            return new StopSector(station, track, type, isSouth, wagonCount, sector);
        });

        if(sectors.isEmpty()) {
            throw new IllegalStateException("Sectors are not initialized!");
        }

        initialize(sectors);
        log.info("Initialized StopSectorUpdater with {} sectors", sectors.size());
    }

    public void initialize(final List<StopSector> stopSectors) {
        stopSectors.forEach(sector -> {
            stationMap.putIfAbsent(sector.station, new TrackMap());
            final var trackMap = stationMap.get(sector.station);

            trackMap.putIfAbsent(sector.track, new DirectionMap());
            final var directionMap = trackMap.get(sector.track);

            directionMap.putIfAbsent(sector.isSouth, new TypeMap());
            final var typeMap = directionMap.get(sector.isSouth);

            typeMap.putIfAbsent(sector.type, new WagonCountToSectorMap());
            final var wagonCountMap = typeMap.get(sector.type);

            wagonCountMap.put(sector.wagonCount, sector.sector);
        });
    }

    public boolean hasStation(final String station) {
        return stationMap.containsKey(station);
    }

    public String findStopSector(final TimeTableRow row, final String type, final boolean south, final int wagonCount) {
        final var trackMap = stationMap.get(row.station.stationShortCode);

        if(trackMap != null) {
            final var directionMap = trackMap.get(row.commercialTrack);

            if(directionMap != null) {
                final var typeMap = directionMap.get(south);

                if(typeMap != null) {
                    final var wagonCountMap = typeMap.get(type);

                    if(wagonCountMap != null) {
                        return wagonCountMap.get(wagonCount);
                    }
                }
            }
        }

        return null;
    }
}
