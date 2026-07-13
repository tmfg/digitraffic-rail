package fi.livi.rata.avoindata.updater.deserializers;

/**
 * Thrown when a single PALA unit fails to deserialize. Carries the offending train number (the response object key)
 * and a capped JSON snippet of the unit so the ingestion cycle can log actionable diagnostics without dumping the
 * whole PALA response.
 */
public class PalaDeserializationException extends RuntimeException {
    private final String trainNumber;
    private final String sampleJson;

    public PalaDeserializationException(final String trainNumber, final String sampleJson, final Throwable cause) {
        super("Failed to deserialize PALA unit for train " + trainNumber, cause);
        this.trainNumber = trainNumber;
        this.sampleJson = sampleJson;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getSampleJson() {
        return sampleJson;
    }
}
