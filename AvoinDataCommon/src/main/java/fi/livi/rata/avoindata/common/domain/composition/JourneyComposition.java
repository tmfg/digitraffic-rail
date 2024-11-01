package fi.livi.rata.avoindata.common.domain.composition;

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
    public final int totalLength;
    public final int maximumSpeed;

    public final Long attapId;
    public final Long saapAttapId;

    public final JourneyCompositionRow startStation;
    public final JourneyCompositionRow endStation;
    public final long id;

    public long version;
    public final Collection<Wagon> wagons;
    public final Collection<Locomotive> locomotives;
    public JourneySection journeySection;

    public JourneyComposition(final Operator operator, final Long trainNumber, final LocalDate departureDate,
                              final long trainCategoryId, final long trainTypeId, final int totalLength, final int maximumSpeed, final long version,
                              final Collection<Wagon> wagons, final Collection<Locomotive> locomotives, final JourneyCompositionRow startStation,
                              final JourneyCompositionRow endStation, final long id,
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
        this.wagons = wagons;
        this.locomotives = locomotives;
        this.startStation = startStation;
        this.endStation = endStation;

        this.id = id;
        this.attapId = attapId;
        this.saapAttapId = saapAttapId;
    }
}
