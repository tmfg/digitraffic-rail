package fi.livi.rata.avoindata.updater.service.siri.common;

/**
 * A resolved published-journey reference, shared by SIRI-ET and SIRI-VM.
 *
 * @param origin      the journey's first commercial stop (station + planned track), or {@code null} when the
 *                    published journey has no tracks. Currently only consumed by SIRI-VM (see
 *                    {@code VmJourneyInterpreter}) to resolve the optional {@code OriginRef}/{@code OriginName}.
 * @param destination the journey's last commercial stop, or {@code null}; consumed the same way for
 *                    {@code DestinationRef}/{@code DestinationName}.
 */
public record ResolvedJourney(ServiceJourneyId serviceJourneyId, DataFrameRef dataFrameRef, LineId lineId,
                              OperatorRef operatorRef, JourneyPatternRef journeyPatternRef,
                              JourneyEndpoint origin, JourneyEndpoint destination) {

    /** Convenience constructor for callers (and existing tests) that don't care about origin/destination. */
    public ResolvedJourney(final ServiceJourneyId serviceJourneyId, final DataFrameRef dataFrameRef,
                           final LineId lineId, final OperatorRef operatorRef,
                           final JourneyPatternRef journeyPatternRef) {
        this(serviceJourneyId, dataFrameRef, lineId, operatorRef, journeyPatternRef, null, null);
    }
}
