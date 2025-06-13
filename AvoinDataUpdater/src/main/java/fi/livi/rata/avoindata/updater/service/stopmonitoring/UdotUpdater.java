package fi.livi.rata.avoindata.updater.service.stopmonitoring;

import fi.livi.rata.avoindata.common.domain.stopmonitoring.UdotData;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Update udot information into TimeTableRows.
 *
 * This only updates the value if the existing value is nonnull, or the new
 * value is true.
 *
 * For each row after the row specified in Udot, use the same unknownDelay
 *
 * Updates train version.
 */
public class UdotUpdater {
    private static Logger log = LoggerFactory.getLogger(UdotUpdater.class);

    public static void updateUdotInformation(final UdotData udot, final TimeTableRow row) {
        if(row.unknownDelay != null || udot.getUnknownDelay()) {
            row.unknownDelay = udot.getUnknownDelay();
        }

        if(row.unknownTrack != null || udot.getUnknownTrack()) {
            row.unknownTrack = udot.getUnknownTrack();
        }
    }

    public static void updateUdotInformation(final UdotData udot, final Train train, final long newVersion) {
        boolean rowFound = false;
        Boolean unknownDelay = null;

        for(final TimeTableRow r : train.timeTableRows) {
            if(r.id.attapId.intValue() == udot.getAttapId()) {
                updateUdotInformation(udot, r);
                rowFound = true;
                unknownDelay = r.unknownDelay;
            } else if(rowFound && unknownDelay != null) {
                r.unknownDelay = unknownDelay;
            }
        };


        if(rowFound) {
            log.info("Updating train version from {} -> {}", train.version, newVersion);
            train.version = newVersion;
        } else {
            log.error("Could not find row for {}", udot.getAttapId());
        }
    }
}
