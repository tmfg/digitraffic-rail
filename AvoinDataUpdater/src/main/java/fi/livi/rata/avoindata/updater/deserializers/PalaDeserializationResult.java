package fi.livi.rata.avoindata.updater.deserializers;

import java.util.List;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;

/**
 * Outcome of parsing one PALA {@code /0.2/yksikot.json} response: the successfully parsed locations plus the
 * per-cycle counts the ingestion wide-event log needs.
 *
 * @param locations             successfully parsed train locations
 * @param receivedCount         number of units in the raw PALA response (the coverage denominator)
 * @param droppedNoCoordinate   units dropped because PALA has no {@code sijainti.koordinaatti} — a legitimate
 *                              "no position known" case per the PALA team, not a data-quality error
 * @param droppedNoSpeed        units dropped because {@code nopeus} is null/absent — PALA "speed unknown"
 * @param deserializationErrors units that failed validation/parsing; each is skipped, none aborts the batch
 * @param firstErrorTrainNumber train number (digits only) of the first deserialization error, or {@code null}
 * @param firstErrorSampleJson  capped JSON snippet of the first failing unit, or {@code null}
 */
public record PalaDeserializationResult(
        List<TrainLocation> locations,
        int receivedCount,
        int droppedNoCoordinate,
        int droppedNoSpeed,
        int deserializationErrors,
        String firstErrorTrainNumber,
        String firstErrorSampleJson) {
}
