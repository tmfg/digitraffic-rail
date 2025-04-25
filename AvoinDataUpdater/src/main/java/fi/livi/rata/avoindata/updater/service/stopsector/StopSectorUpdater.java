package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Service
public class StopSectorUpdater {
    private final TrainTypeRepository trainTypeRepository;
    private final TimeTableRowRepository timeTableRowRepository;

    private final SectorMap sectorMap = new SectorMap();
    private final DirectionMap directionMap = new DirectionMap();

    private static final Logger log = LoggerFactory.getLogger(StopSectorUpdater.class);

    public StopSectorUpdater(final TrainTypeRepository trainTypeRepository, final TimeTableRowRepository timeTableRowRepository) {
        this.trainTypeRepository = trainTypeRepository;
        this.timeTableRowRepository = timeTableRowRepository;

        this.initialize();
    }

    private void initialize() {
        sectorMap.initialize("sectors/sectors.csv");

        directionMap.initialize("sectors/directions.csv");
    }

    private String getType(final TimeTableRow row, final JourneySection journeySection) {
        // if trainCategory is long-distance, use train-type
        // otherwise use locomotive type
        if(row.train.trainCategoryId == 1) {
            // long-distance
            final var trainType = trainTypeRepository.findByIdCached(row.train.trainTypeId);

            if(trainType.isEmpty()) {
                throw new IllegalStateException("No train type found for id " + row.train.trainTypeId);
            }

            return trainType.get().name;
        }

        return journeySection.locomotives.iterator().next().locomotiveType;
    }

    /**
     * Get active journey section from given composition that is active on give row.
     * <p>
     * This is done by iterating all rows from the beginning and same time iterating all journeysections from composition.
     * If iterated row is the given row, then return the current journey section.
     * If current journeysection ends in the iterated row, iterate to text journeysection.
     */
    private JourneySection getJourneySection(final TimeTableRow currentRow, final Composition composition) {
        final Iterator<JourneySection> jsIterator = composition.journeySections.iterator();
        JourneySection currentJourneySection = jsIterator.next();

        for(final TimeTableRow row : currentRow.train.timeTableRows) {
            if(row == currentRow) {
                return currentJourneySection;
            }

            if(currentJourneySection.endTimeTableRow.station.stationShortCode.equals(currentRow.station.stationShortCode) && jsIterator.hasNext()) {
                currentJourneySection= jsIterator.next();
            }
        }

        return null;
    }

    // update all stop sectors
    public void updateStopSectors(final Train train, final Composition composition, final String source) {
//        TimeTableRow current = null;

        // go through all commercial rows and update sectors for them
        // does not handle the last station and does not need to
        for (int i = 0; i < train.timeTableRows.size() - 1; i++) {
            final TimeTableRow current = train.timeTableRows.get(i);

            if (BooleanUtils.isTrue(current.commercialStop) && current.commercialTrack != null) {
                final var journeySection = getJourneySection(current, composition);
                final Boolean isSouth = directionMap.isSouth(train, i);

                // should we limit with actualTime?

                if (journeySection == null) {
                    log.error("No journey section found for {}", current);
                } else if (isSouth == null) {
                    if (directionMap.hasStation(current.station.stationShortCode)) {
                        log.warn("Could not find direction from {} for train {} {}", current.station.stationShortCode, train.id.trainNumber, train.id.departureDate);
                    }
                } else {
                    final var updated = updateStopSector(current, journeySection, isSouth, source);

                    if(updated) {
                        timeTableRowRepository.save(current);
                    }
                }
            }
        }
    }

    private String createStopSectorString(final TimeTableRow row, final String type, final boolean south, final int size) {
        return String.format("%s,%s,%s,%s,%d", row.station.stationShortCode, row.commercialTrack, type, south ? "SOUTH" : "NORTH", size);
    }

    public boolean updateStopSector(final TimeTableRow row, final JourneySection journeySection, final Boolean south, final String source) {
        final var type = getType(row, journeySection);
        final String stopSector = sectorMap.findStopSector(row, type, south, journeySection.wagons.size());

        if(stopSector == null) {
            if (sectorMap.hasStation(row.station.stationShortCode)) {
                log.info("No stop sector for {} missingSector={}", row.id, createStopSectorString(row, type, south, journeySection.wagons.size()));
            }
        }

        if(!StringUtils.equals(row.stopSector, stopSector)) {
            log.info("method=updateStopSector trainNumber={} departureDate={} stopSector={} oldSector={} source={}",
                    row.id.trainNumber, row.id.departureDate, stopSector, firstNonNull(row.stopSector, "NULL"), source);
            row.stopSector = stopSector;

            return true;
        }

        return false;
    }
}
