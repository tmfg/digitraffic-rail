package fi.livi.rata.avoindata.updater.service.siri.common;

public record ResolvedJourney(ServiceJourneyId serviceJourneyId, DataFrameRef dataFrameRef, LineId lineId,
                              OperatorRef operatorRef, JourneyPatternRef journeyPatternRef) {}
