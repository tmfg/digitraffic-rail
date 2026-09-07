package fi.livi.rata.avoindata.updater.service.netex;

import java.util.List;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * A per-{@code (trainNumber, departureDate)} published-journey aggregate: everything
 * {@link NeTExPublishedJourneyWriter} needs to persist one journey row and its tracks, already joined from the
 * winning schedule and its built {@code ServiceJourney}.
 */
public record PublishedJourneyDraft(TrainId trainId, String serviceJourneyId, String lineRef, String operatorRef,
        String journeyPatternRef, List<PublishedTrack> tracks) {

    /** A planned commercial track for one station on the journey. */
    public record PublishedTrack(String stationShortCode, String plannedTrack) {}
}
