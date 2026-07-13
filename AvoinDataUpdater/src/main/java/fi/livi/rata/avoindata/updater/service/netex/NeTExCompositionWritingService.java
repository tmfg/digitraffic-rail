package fi.livi.rata.avoindata.updater.service.netex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.rutebanken.netex.model.AccessibilityAssessment;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.FareClassEnumeration;
import org.rutebanken.netex.model.FuelTypeEnumeration;
import org.rutebanken.netex.model.JourneysInFrame_RelStructure;
import org.rutebanken.netex.model.LimitationStatusEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.ServiceJourneyRefStructure;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.Train;
import org.rutebanken.netex.model.TrainComponent;
import org.rutebanken.netex.model.TrainComponents_RelStructure;
import org.rutebanken.netex.model.TrainElement;
import org.rutebanken.netex.model.TrainElementTypeEnumeration;
import org.rutebanken.netex.model.TrainRefStructure;
import org.rutebanken.netex.model.TrainSizeStructure;
import org.rutebanken.netex.model.VehicleType;
import org.rutebanken.netex.model.VehicleTypesInFrame_RelStructure;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExDatedVehicleJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExTrainComponent;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExVehicleType;

/**
 * Builds NeTEx compositions PublicationDelivery and delegates
 * marshalling/zipping
 * to NeTExWritingService.
 */
@Service
public class NeTExCompositionWritingService {

    private static final String NETEX_VERSION = "1.15:NO-NeTEx-networktimetable:1.5";
    private static final ObjectFactory FACTORY = new ObjectFactory();

    private final NeTExWritingService writingService;

    public NeTExCompositionWritingService(final NeTExWritingService writingService) {
        this.writingService = writingService;
    }

    public byte[] writeZip(final List<NeTExVehicleType> vehicleTypes,
            final List<NeTExDatedVehicleJourney> datedJourneys,
            final ZonedDateTime timestamp) {
        final PublicationDeliveryStructure delivery = buildPublicationDelivery(vehicleTypes, datedJourneys, timestamp);
        return writingService.marshalAndZip(delivery, "DT_rail_compositions.xml");
    }

    private PublicationDeliveryStructure buildPublicationDelivery(
            final List<NeTExVehicleType> vehicleTypes,
            final List<NeTExDatedVehicleJourney> datedJourneys,
            final ZonedDateTime timestamp) {

        final List<Train> trains = buildTrains(datedJourneys);
        final ResourceFrame resourceFrame = buildResourceFrame(vehicleTypes, trains);
        final TimetableFrame timetableFrame = buildTimetableFrame(datedJourneys);

        final PublicationDeliveryStructure.DataObjects dataObjects = new PublicationDeliveryStructure.DataObjects()
                .withCompositeFrameOrCommonFrame(
                        FACTORY.createResourceFrame(resourceFrame),
                        FACTORY.createTimetableFrame(timetableFrame));

        return new PublicationDeliveryStructure()
                .withVersion(NETEX_VERSION)
                .withPublicationTimestamp(timestamp.toLocalDateTime())
                .withParticipantRef("DT")
                .withDescription(new MultilingualString().withValue("Finland rail composition and accessibility data"))
                .withDataObjects(dataObjects);
    }

    private ResourceFrame buildResourceFrame(final List<NeTExVehicleType> vehicleTypes, final List<Train> trains) {
        final VehicleTypesInFrame_RelStructure vtStruct = new VehicleTypesInFrame_RelStructure();

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

            vtStruct.getCompoundTrainOrTrainOrVehicleType().add(vehicleType);
        }

        for (final Train train : trains) {
            vtStruct.getCompoundTrainOrTrainOrVehicleType().add(train);
        }

        return new ResourceFrame()
                .withId("DT:ResourceFrame:compositions")
                .withVersion("1")
                .withVehicleTypes(vtStruct);
    }

    private List<Train> buildTrains(final List<NeTExDatedVehicleJourney> datedJourneys) {
        final List<Train> trains = new ArrayList<>();

        for (final NeTExDatedVehicleJourney dj : datedJourneys) {
            final String idSuffix = dj.trainNumber() + "-" + dj.date()
                    + (dj.beginStation() != null ? "-" + dj.beginStation() : "");
            final String trainId = "DT:Train:" + idSuffix;

            final TrainComponents_RelStructure components = new TrainComponents_RelStructure();
            for (final NeTExTrainComponent comp : dj.components()) {
                final TrainElement element = new TrainElement()
                        .withId("DT:TrainElement:" + idSuffix + "-" + comp.order())
                        .withVersion("1")
                        .withTrainElementType(TrainElementTypeEnumeration.fromValue(comp.elementType()));

                if (comp.fareClass() != null) {
                    element.withFareClasses(mapFareClassEnum(comp.fareClass()));
                }

                final TrainComponent trainComponent = new TrainComponent()
                        .withId("DT:TrainComponent:" + idSuffix + "-" + comp.order())
                        .withVersion("1")
                        .withOrder(BigInteger.valueOf(comp.order()))
                        .withLabel(new MultilingualString().withValue(comp.label()))
                        .withTrainElement(element);

                components.getTrainComponentRefOrTrainComponent().add(trainComponent);
            }

            trains.add(new Train()
                    .withId(trainId)
                    .withVersion("1")
                    .withTrainSize(new TrainSizeStructure()
                            .withNumberOfCars(BigInteger.valueOf(dj.components().size())))
                    .withComponents(components));
        }

        return trains;
    }

    private TimetableFrame buildTimetableFrame(final List<NeTExDatedVehicleJourney> datedJourneys) {
        final JourneysInFrame_RelStructure vehicleJourneys = new JourneysInFrame_RelStructure();

        for (final NeTExDatedVehicleJourney dj : datedJourneys) {
            final String idSuffix = dj.trainNumber() + "-" + dj.date()
                    + (dj.beginStation() != null ? "-" + dj.beginStation() : "");
            final String trainId = "DT:Train:" + idSuffix;

            final TrainRefStructure trainRef = new TrainRefStructure();
            trainRef.setRef(trainId);

            final DatedServiceJourney dsj = new DatedServiceJourney()
                    .withId(dj.id())
                    .withVersion("1")
                    .withVehicleTypeRef(FACTORY.createTrainRef(trainRef))
                    .withTrainSize(new TrainSizeStructure()
                            .withNumberOfCars(BigInteger.valueOf(dj.components().size())));

            if (dj.serviceJourneyRef() != null) {
                final ServiceJourneyRefStructure sjRef = new ServiceJourneyRefStructure();
                sjRef.setRef(dj.serviceJourneyRef());
                dsj.withJourneyRef(FACTORY.createServiceJourneyRef(sjRef));
            }

            if (dj.hasWheelchair()) {
                dsj.withAccessibilityAssessment(new AccessibilityAssessment()
                        .withId("DT:AA:" + idSuffix)
                        .withVersion("1")
                        .withMobilityImpairedAccess(LimitationStatusEnumeration.TRUE));
            }

            vehicleJourneys.getVehicleJourneyOrDatedVehicleJourneyOrNormalDatedVehicleJourney().add(dsj);
        }

        return new TimetableFrame()
                .withId("DT:TimetableFrame:compositions")
                .withVersion("1")
                .withVehicleJourneys(vehicleJourneys);
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
