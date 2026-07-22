package fi.livi.rata.avoindata.updater.service.netex.peti;

/**
 * Telemetry value object for a PETI fetch operation.
 *
 * @param operation    fixed literal "fetchPeti"
 * @param outcome      "success" or "error"
 * @param httpStatus   HTTP response status code (0 for network-level failures)
 * @param durationMs   request duration in milliseconds
 * @param stopPlaces   number of parsed StopPlaces (0 on error)
 * @param quays        number of parsed Quays (0 on error)
 * @param bodySize     response body size in bytes (0 on error)
 * @param errorType    exception class name on error, null on success
 */
public record PetiFetchResult(
        String operation,
        String outcome,
        int httpStatus,
        long durationMs,
        int stopPlaces,
        int quays,
        long bodySize,
        String errorType
) {

    public static final String OPERATION_FETCH_PETI = "fetchPeti";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";

    public static PetiFetchResult success(final int httpStatus, final long durationMs,
            final int stopPlaces, final int quays, final long bodySize) {
        return new PetiFetchResult(OPERATION_FETCH_PETI, OUTCOME_SUCCESS, httpStatus, durationMs,
                stopPlaces, quays, bodySize, null);
    }

    public static PetiFetchResult error(final int httpStatus, final long durationMs,
            final long bodySize, final String errorType) {
        return new PetiFetchResult(OPERATION_FETCH_PETI, OUTCOME_ERROR, httpStatus, durationMs,
                0, 0, bodySize, errorType);
    }
}
