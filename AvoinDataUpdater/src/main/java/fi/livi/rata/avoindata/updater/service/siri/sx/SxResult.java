package fi.livi.rata.avoindata.updater.service.siri.sx;

import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxSituation;

/**
 * Situations that were written and counts of what was left out.
 *
 * @param noJourneyIds ids of train messages whose train is not in the published journeys
 */
public record SxResult(List<SxSituation> situations, int noText, int nextStop, int noStopPlace,
                       int unknownStations, List<String> noJourneyIds) {

    public int dropped() {
        return noText + nextStop + noStopPlace + noJourneyIds.size();
    }
}
