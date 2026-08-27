package fi.livi.rata.avoindata.updater.deserializers;

/**
 * Thrown when a single PALA unit fails to deserialize. Carries the offending train number (the response object key)
 * and a capped JSON snippet of the unit so the ingestion cycle can log actionable diagnostics without dumping the
 * whole PALA response.
 */
public class PalaDeserializationException extends RuntimeException {

    /** Cap for the offending-unit JSON snippet (~2 KB). */
    private static final int MAX_SAMPLE_JSON_LENGTH = 2048;

    private final String trainNumber;
    private final String sampleJson;
    private final String reason;

    /** A validation failure with a known machine-friendly reason and no underlying cause. */
    public PalaDeserializationException(final String trainNumber, final String rawUnitJson, final String reason) {
        super("Failed to deserialize PALA unit for train " + trainNumber + " reason=" + reason);
        this.trainNumber = trainNumber;
        this.sampleJson = truncate(rawUnitJson);
        this.reason = reason;
    }

    /** A parse failure wrapping the underlying cause; the reason is the cause's simple class name. */
    public PalaDeserializationException(final String trainNumber, final String rawUnitJson, final Throwable cause) {
        super("Failed to deserialize PALA unit for train " + trainNumber, cause);
        this.trainNumber = trainNumber;
        this.sampleJson = truncate(rawUnitJson);
        this.reason = cause.getClass().getSimpleName();
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getSampleJson() {
        return sampleJson;
    }

    public String getReason() {
        return reason;
    }

    /** Caps the JSON snippet at {@link #MAX_SAMPLE_JSON_LENGTH} without splitting a UTF-16 surrogate pair. */
    private static String truncate(final String json) {
        if (json == null || json.length() <= MAX_SAMPLE_JSON_LENGTH) {
            return json;
        }
        int end = MAX_SAMPLE_JSON_LENGTH;
        if (Character.isHighSurrogate(json.charAt(end - 1))) {
            end--;
        }
        return json.substring(0, end);
    }
}
