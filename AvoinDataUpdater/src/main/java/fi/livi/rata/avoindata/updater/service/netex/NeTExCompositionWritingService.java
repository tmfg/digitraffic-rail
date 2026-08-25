package fi.livi.rata.avoindata.updater.service.netex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rutebanken.netex.model.AccessibilityAssessment;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.FareClassEnumeration;
import org.rutebanken.netex.model.FuelTypeEnumeration;
import org.rutebanken.netex.model.LimitationStatusEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.Train;
import org.rutebanken.netex.model.TrainComponent;
import org.rutebanken.netex.model.TrainComponents_RelStructure;
import org.rutebanken.netex.model.TrainElement;
import org.rutebanken.netex.model.TrainElementTypeEnumeration;
import org.rutebanken.netex.model.TrainRefStructure;
import org.rutebanken.netex.model.TrainSizeStructure;
import org.rutebanken.netex.model.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExDatedVehicleJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExTrainComponent;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExVehicleType;

/**
 * Builds the NeTEx objects that carry train formation: VehicleTypes for the
 * shared file, Trains for a Line file, and the composition attributes folded
 * onto that Line's DatedServiceJourneys.
 */
@Service
public class NeTExCompositionWritingService {

    private static final Logger log = LoggerFactory.getLogger(NeTExCompositionWritingService.class);
    private static final ObjectFactory FACTORY = new ObjectFactory();

    private final NeTExIdGenerator idGenerator;

    public NeTExCompositionWritingService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Keys compositions by the DatedServiceJourney id the timetable generates for
     * the same (train, day), so a Line file can find the formation for each of its
     * journeys.
     * <p>
     * A train that changes formation en route has one composition per journey
     * section, but the timetable has a single journey for that day. Only the
     * section leaving the origin is kept; the rest are counted and reported.
     */
    public Map<String, NeTExDatedVehicleJourney> indexByDatedServiceJourneyId(
            final List<NeTExDatedVehicleJourney> datedJourneys) {
        final Map<String, NeTExDatedVehicleJourney> byId = new LinkedHashMap<>();
        int laterSections = 0;
        for (final NeTExDatedVehicleJourney dj : datedJourneys) {
            final String key = idGenerator.datedServiceJourneyId(dj.trainNumber(), dj.date());
            if (byId.putIfAbsent(key, dj) != null) {
                laterSections++;
            }
        }
        if (laterSections > 0) {
            log.info("method=indexByDatedServiceJourneyId dropped_later_journey_sections={}", laterSections);
        }
        return byId;
    }

    public List<VehicleType> buildVehicleTypes(final List<NeTExVehicleType> vehicleTypes) {
        final List<VehicleType> result = new ArrayList<>();
        for (final NeTExVehicleType vt : vehicleTypes) {
            final VehicleType vehicleType = new VehicleType()
                    .withId(vt.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(vt.name()))
                    .withSelfPropelled(vt.selfPropelled());

            if (vt.fuelType() != null) {
                vehicleType.withTypeOfFuel(FuelTypeEnumeration.fromValue(mapFuelType(vt.fuelType())));
            }
            if (vt.wheelchair()) {
                vehicleType.withLowFloor(true);
                vehicleType.withHasLiftOrRamp(true);
            }
            if (vt.lengthCm() > 0) {
                vehicleType.withLength(BigDecimal.valueOf(vt.lengthCm()).movePointLeft(2));
            }
            result.add(vehicleType);
        }
        return result;
    }

    public Train buildTrain(final NeTExDatedVehicleJourney dj) {
        final String idSuffix = trainIdSuffix(dj);
        final TrainComponents_RelStructure components = new TrainComponents_RelStructure();

        for (final NeTExTrainComponent comp : dj.components()) {
            final TrainElement element = new TrainElement()
                    .withId("FTR:TrainElement:" + idSuffix + "-" + comp.order())
                    .withVersion("1")
                    .withTrainElementType(TrainElementTypeEnumeration.fromValue(comp.elementType()));

            if (comp.fareClass() != null) {
                element.withFareClasses(mapFareClassEnum(comp.fareClass()));
            }

            components.getTrainComponentRefOrTrainComponent().add(new TrainComponent()
                    .withId("FTR:TrainComponent:" + idSuffix + "-" + comp.order())
                    .withVersion("1")
                    .withOrder(BigInteger.valueOf(comp.order()))
                    .withLabel(new MultilingualString().withValue(comp.label()))
                    .withTrainElement(element));
        }

        return new Train()
                .withId("FTR:Train:" + idSuffix)
                .withVersion("1")
                .withTrainSize(new TrainSizeStructure()
                        .withNumberOfCars(BigInteger.valueOf(dj.components().size())))
                .withComponents(components);
    }

    /**
     * Folds formation and accessibility onto the journey the timetable already
     * produced, so one DatedServiceJourney carries both schedule and composition.
     */
    public void applyComposition(final DatedServiceJourney dsj, final NeTExDatedVehicleJourney dj) {
        final TrainRefStructure trainRef = new TrainRefStructure();
        trainRef.setRef("FTR:Train:" + trainIdSuffix(dj));

        dsj.withVehicleTypeRef(FACTORY.createTrainRef(trainRef))
                .withTrainSize(new TrainSizeStructure()
                        .withNumberOfCars(BigInteger.valueOf(dj.components().size())));

        if (dj.hasWheelchair()) {
            dsj.withAccessibilityAssessment(new AccessibilityAssessment()
                    .withId("FTR:AccessibilityAssessment:" + trainIdSuffix(dj))
                    .withVersion("1")
                    .withMobilityImpairedAccess(LimitationStatusEnumeration.TRUE));
        }
    }

    private static String trainIdSuffix(final NeTExDatedVehicleJourney dj) {
        return dj.trainNumber() + "-" + dj.date()
                + (dj.beginStation() != null ? "-" + dj.beginStation() : "");
    }

    private static String mapFuelType(final String powerType) {
        if (powerType == null)
            return "other";
        return switch (powerType.toLowerCase()) {
            case "electricity", "electric", "s" -> "electricity";
            case "diesel", "d" -> "diesel";
            default -> "other";
        };
    }

    private static FareClassEnumeration mapFareClassEnum(final String fareClass) {
        return switch (fareClass) {
            case "firstClass" -> FareClassEnumeration.FIRST_CLASS;
            case "standardClass" -> FareClassEnumeration.STANDARD_CLASS;
            default -> FareClassEnumeration.ANY;
        };
    }
}
