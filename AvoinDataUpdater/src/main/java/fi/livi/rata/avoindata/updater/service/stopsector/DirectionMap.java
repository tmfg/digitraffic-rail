package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.CsvUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direction map contains the direction information from given station to some stations around it.
 * The second stations are not the next station in the infra and not the next stop in the time table row, just some station.
 *
 * So, when determining the direction, we have to walk through the time table rows and for each station check if we
 * have the direction information from the given station to it.
 */
public class DirectionMap {
    // map from start station -> destination station and if the direction is south
    private final Map<String, Map<String, Boolean>> directionMap = new HashMap<>();

    private static final Logger log = LoggerFactory.getLogger(DirectionMap.class);

    public record StopSectorDirection(String stationFrom, String stationTo, boolean south) {
    }

    public void initialize(final List<StopSectorDirection> directions) {
        directions.forEach(direction ->
                addMapping(direction.stationFrom(), direction.stationTo(), direction.south()));
    }

    public void initialize(final String fileName) {
        final var directions = CsvUtil.readFile(fileName, t -> {
            final String fromStation = t[1];
            final String toStation = t[3];
            final boolean south = StringUtils.equals("SOUTH", t[4]);

            return new DirectionMap.StopSectorDirection(fromStation, toStation, south);
        });

        if(directions.isEmpty()) {
            throw new IllegalStateException("Directions are not initialized!");
        }

        initialize(directions);
        log.info("Initialized StopSectorUpdater with {} directions", directions.size());
    }

    private void addMapping(final String from, final String to, final boolean south) {
        directionMap.computeIfAbsent(from, (key) -> new HashMap<>());

        directionMap.get(from).put(to, south);
    }

    public Map<String, Boolean> getEntries(final String station) {
        return directionMap.get(station);
    }

    public boolean hasStation(final String station) {
        return getEntries(station) != null;
    }

    public Boolean isSouth(final TimeTableRow fromRow) {
        final var entries = getEntries(fromRow.station.stationShortCode);

        // find the given row from the timetable rows
        boolean rowFound = false;

        if(entries != null) {
            for(final var toRow : fromRow.train.timeTableRows) {
                if(toRow.type == TimeTableRow.TimeTableRowType.ARRIVAL) {
                    if (rowFound) {
                        // and check each station after that, if we know the direction
                        final var isSouth = entries.get(toRow.station.stationShortCode);
                        if (isSouth != null) {
                            return isSouth;
                        }

                        // if we come to commercial stop, we can stop searching, we are not going to find anything
                        if (BooleanUtils.isTrue(toRow.commercialStop)) {
                            return null;
                        }
                    } else if (toRow.id.equals(fromRow.id)) {
                        rowFound = true;
                    }
                }
            }

            if(!rowFound) {
                log.error("Could not find row for {}!", fromRow);
            }
        }

        return null;
    }
}
