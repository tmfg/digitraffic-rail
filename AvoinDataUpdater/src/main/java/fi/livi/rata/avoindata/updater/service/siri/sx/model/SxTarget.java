package fi.livi.rata.avoindata.updater.service.siri.sx.model;

import java.util.List;

import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;

/** What a situation is about: some stations or one train. */
public sealed interface SxTarget {

    /** PETI stop place ids, for example {@code FSR:StopPlace:1}. */
    record StopPlaces(List<String> stopPlaceIds) implements SxTarget {}

    /** A published journey. {@code operatorRef} and {@code lineId} may be {@code null}. */
    record Journey(ServiceJourneyId serviceJourneyId, DataFrameRef dataFrameRef, OperatorRef operatorRef,
                   LineId lineId) implements SxTarget {}
}
