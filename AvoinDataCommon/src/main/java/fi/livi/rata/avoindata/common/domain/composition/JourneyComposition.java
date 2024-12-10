package fi.livi.rata.avoindata.common.domain.composition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;

import fi.livi.rata.avoindata.common.domain.common.Operator;

/**
 * Composition for one leg of journey
 */
public class JourneyComposition {
    public final Operator operator;
    public final Long trainNumber;

    public final LocalDate departureDate;
    public final long trainCategoryId;
    public final long trainTypeId;
    public final int totalLength; // meters
    public final int maximumSpeed;

    public final Long attapId;
    public final Long saapAttapId;

    public final JourneyCompositionRow startStation;
    public final JourneyCompositionRow endStation;

    public long version;
    public final Collection<Wagon> wagons;
    public final Collection<Locomotive> locomotives;
    public JourneySection journeySection;
    public Instant messageDateTime;

    public JourneyComposition(final Operator operator,
                              final Long trainNumber,
                              final LocalDate departureDate,
                              final long trainCategoryId,
                              final long trainTypeId,
                              final int totalLength,
                              final int maximumSpeed,
                              final long version,
                              final Instant messageDateTime,
                              final Collection<Wagon> wagons,
                              final Collection<Locomotive> locomotives,
                              final JourneyCompositionRow startStation,
                              final JourneyCompositionRow endStation,
                              final Long attapId,
                              final Long saapAttapId

    ) {
        this.operator = operator;
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
        this.trainCategoryId = trainCategoryId;
        this.trainTypeId = trainTypeId;
        this.totalLength = totalLength;
        this.maximumSpeed = maximumSpeed;

        this.version = version;
        this.messageDateTime = messageDateTime;
        this.wagons = wagons;
        this.locomotives = locomotives;
        this.startStation = startStation;
        this.endStation = endStation;

        this.attapId = attapId;
        this.saapAttapId = saapAttapId;
    }

    @Override
    public String toString() {
        return "JourneyComposition{" +
                "operator=" + operator +
                ", trainNumber=" + trainNumber +
                ", departureDate=" + departureDate +
                ", trainCategoryId=" + trainCategoryId +
                ", trainTypeId=" + trainTypeId +
                ", totalLength=" + totalLength +
                ", maximumSpeed=" + maximumSpeed +
                ", attapId=" + attapId +
                ", saapAttapId=" + saapAttapId +
                ", startStation=" + startStation +
                ", endStation=" + endStation +
                ", version=" + version +
                ", messageReference=" + messageDateTime +
                ", wagons=" + wagons +
                ", locomotives=" + locomotives +
                ", journeySection=" + journeySection +
                '}';
    }
}
