package fi.livi.rata.avoindata.updater.service.netex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.AvailabilityCondition;
import org.rutebanken.netex.model.Codespace;
import org.rutebanken.netex.model.Codespaces_RelStructure;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.DatedServiceJourney;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.DestinationDisplayRefStructure;
import org.rutebanken.netex.model.DestinationDisplaysInFrame_RelStructure;
import org.rutebanken.netex.model.Frames_RelStructure;
import org.rutebanken.netex.model.GroupOfLinesRefStructure;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternRefStructure;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.JourneysInFrame_RelStructure;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.LineRefStructure;
import org.rutebanken.netex.model.LinesInFrame_RelStructure;
import org.rutebanken.netex.model.LocaleStructure;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.OperatingDay;
import org.rutebanken.netex.model.OperatingDayRefStructure;
import org.rutebanken.netex.model.OperatingDaysInFrame_RelStructure;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.OperatorRefStructure;
import org.rutebanken.netex.model.OrganisationsInFrame_RelStructure;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointOnRoute;
import org.rutebanken.netex.model.PointsInJourneyPattern_RelStructure;
import org.rutebanken.netex.model.PointsOnRoute_RelStructure;
import org.rutebanken.netex.model.PrivateCodeStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.QuayRefStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RoutePoint;
import org.rutebanken.netex.model.RoutePointRefStructure;
import org.rutebanken.netex.model.RoutePointsInFrame_RelStructure;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.RoutesInFrame_RelStructure;
import org.rutebanken.netex.model.ScheduledStopPoint;
import org.rutebanken.netex.model.ScheduledStopPointRefStructure;
import org.rutebanken.netex.model.ScheduledStopPointsInFrame_RelStructure;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.ServiceJourneyRefStructure;
import org.rutebanken.netex.model.StopAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.StopPlaceRefStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.StopPointInJourneyPatternRefStructure;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.rutebanken.netex.model.TimetabledPassingTimes_RelStructure;
import org.rutebanken.netex.model.Train;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.rutebanken.netex.model.VehicleType;
import org.rutebanken.netex.model.VehicleTypesInFrame_RelStructure;
import org.rutebanken.netex.model.VersionFrameDefaultsStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

/**
 * Serializes NeTEx domain objects to XML and produces the final ZIP output.
 * Uses Entur netex-java-model (JAXB) for type-safe marshalling.
 */
@Service
public class NeTExWritingService {

    private static final Logger log = LoggerFactory.getLogger(NeTExWritingService.class);
    private static final String SHARED_DATA_XML = NeTExFileNaming.SHARED_DATA_XML;
    private static final String VERSION = "1.15:NO-NeTEx-networktimetable:1.5";
    // Latest arrival of a journey belonging to the previous operating day.
    private static final LocalTime SERVICE_DAY_END = LocalTime.of(4, 0);
    private static final ObjectFactory FACTORY = new ObjectFactory();

    private final NeTExIdGenerator idGenerator;
    private final NeTExCompositionWritingService compositionWriter;

    public NeTExWritingService(final NeTExIdGenerator idGenerator,
            final NeTExCompositionWritingService compositionWriter) {
        this.idGenerator = idGenerator;
        this.compositionWriter = compositionWriter;
    }

    private volatile JAXBContext jaxbContext;

    private JAXBContext getJaxbContext() {
        if (jaxbContext == null) {
            synchronized (this) {
                if (jaxbContext == null) {
                    try {
                        jaxbContext = JAXBContext.newInstance(PublicationDeliveryStructure.class);
                    } catch (final JAXBException e) {
                        throw new RuntimeException("Failed to initialize JAXB context", e);
                    }
                }
            }
        }
        return jaxbContext;
    }

    /**
     * Assembles the dataset as a Nordic-profile ZIP: one common file plus one file
     * per Line.
     *
     * @return ZIP file content as byte array
     */
    public byte[] writeNeTExZip(final NeTExStopsData stopsData,
            final NeTExRouteData routeData,
            final NeTExCalendarData calendarData,
            final List<NeTExEntityService.NeTExLine> lines,
            final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            final ZonedDateTime generationTimestamp) {
        return writeNeTExZip(stopsData, routeData, calendarData, lines, operators, serviceJourneys,
                List.of(), generationTimestamp);
    }

    public byte[] writeNeTExZip(final NeTExStopsData stopsData,
            final NeTExRouteData routeData,
            final NeTExCalendarData calendarData,
            final List<NeTExEntityService.NeTExLine> lines,
            final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            final List<NeTExEntityService.NeTExDatedServiceJourney> datedServiceJourneys,
            final ZonedDateTime generationTimestamp) {
        return marshalAndZip(buildDataset(stopsData, routeData, calendarData, lines, operators,
                serviceJourneys, datedServiceJourneys, NeTExCompositionService.CompositionData.empty(),
                generationTimestamp));
    }

    /**
     * Builds every file of the dataset, keyed by ZIP entry name. The common file
     * comes first so that consumers reading sequentially see shared definitions
     * before the line files that reference them.
     */
    public Map<String, PublicationDeliveryStructure> buildDataset(
            final NeTExStopsData stopsData,
            final NeTExRouteData routeData,
            final NeTExCalendarData calendarData,
            final List<NeTExEntityService.NeTExLine> lines,
            final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            final List<NeTExEntityService.NeTExDatedServiceJourney> datedServiceJourneys,
            final NeTExCompositionService.CompositionData compositions,
            final ZonedDateTime generationTimestamp) {

        final NeTExDatasetPartition partition = NeTExDatasetPartition.partition(
                lines, routeData, serviceJourneys, datedServiceJourneys);
        if (!partition.orphans().isEmpty()) {
            log.warn("method=buildDataset dropped orphaned entities routes={} journeyPatterns={} "
                    + "serviceJourneys={} datedServiceJourneys={}",
                    partition.orphans().routes().size(), partition.orphans().journeyPatterns().size(),
                    partition.orphans().serviceJourneys().size(),
                    partition.orphans().datedServiceJourneys().size());
        }

        final List<LocalDate> operatingDays = datedServiceJourneys.stream()
                .map(NeTExEntityService.NeTExDatedServiceJourney::operatingDay)
                .toList();

        final Map<String, NeTExCompositionService.NeTExDatedVehicleJourney> compositionsByJourney = compositionWriter
                .indexByDatedServiceJourneyId(compositions.datedJourneys());
        final Set<String> matched = new HashSet<>();

        final Map<String, PublicationDeliveryStructure> files = new LinkedHashMap<>();
        files.put(SHARED_DATA_XML, buildSharedDelivery(stopsData, operators,
                compositions.vehicleTypes(), operatingDays, generationTimestamp));
        for (final NeTExDatasetPartition.LineSlice slice : partition.lineSlices()) {
            files.put(NeTExFileNaming.lineFileName(slice.line()),
                    buildLineDelivery(slice, compositionsByJourney, matched, generationTimestamp));
        }

        final int unmatched = compositionsByJourney.size() - matched.size();
        if (unmatched > 0) {
            log.warn("method=buildDataset dropped compositions without a timetabled journey count={}", unmatched);
        }
        return files;
    }

    /**
     * Marshals a PublicationDelivery to XML and wraps in a ZIP with the given
     * filename.
     * Shared by timetable and composition writing.
     */
    public byte[] marshalAndZip(final PublicationDeliveryStructure delivery, final String xmlFileName) {
        final String xml = marshalToXml(delivery);
        return zipXml(xml, xmlFileName);
    }

    /**
     * Marshals multiple PublicationDeliveries into a single ZIP, one XML entry per
     * map key. Iteration order is preserved, so pass a LinkedHashMap.
     */
    public byte[] marshalAndZip(final Map<String, PublicationDeliveryStructure> deliveriesByFileName) {
        final Map<String, String> xmlByFileName = new LinkedHashMap<>();
        deliveriesByFileName.forEach((fileName, delivery) -> xmlByFileName.put(fileName, marshalToXml(delivery)));
        return zipXmlFiles(xmlByFileName);
    }

    /**
     * Builds the common file: everything referenced by more than one Line.
     * Carries no TimetableFrame and no Line/Route/JourneyPattern, both of which
     * the Nordic profile forbids in a common file.
     */
    public PublicationDeliveryStructure buildSharedDelivery(
            final NeTExStopsData stopsData,
            final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExCompositionService.NeTExVehicleType> vehicleTypes,
            final Collection<LocalDate> operatingDays,
            final ZonedDateTime generationTimestamp) {

        final CompositeFrame compositeFrame = frame("shared", generationTimestamp,
                buildValidityConditions("shared", operatingDays, generationTimestamp))
                .withFrames(new Frames_RelStructure()
                        .withCommonFrame(
                                FACTORY.createResourceFrame(
                                        buildSharedResourceFrame(operators, vehicleTypes)),
                                FACTORY.createServiceFrame(buildSharedServiceFrame(stopsData)),
                                FACTORY.createServiceCalendarFrame(
                                        buildServiceCalendarFrame(operatingDays))));

        return delivery(generationTimestamp, "Finland rail shared data", compositeFrame);
    }

    /**
     * Builds one line file. Frame ids are qualified with the Line's local id so
     * that no NeTEx id repeats across the files of the dataset. Ids of the
     * compositions consumed here are added to {@code matched} so the caller can
     * report the ones no Line claimed.
     */
    public PublicationDeliveryStructure buildLineDelivery(final NeTExDatasetPartition.LineSlice slice,
            final Map<String, NeTExCompositionService.NeTExDatedVehicleJourney> compositionsByJourney,
            final Set<String> matched,
            final ZonedDateTime generationTimestamp) {
        final String localId = localId(slice.line().id());
        final List<LocalDate> dates = slice.datedServiceJourneys().stream()
                .map(NeTExEntityService.NeTExDatedServiceJourney::operatingDay)
                .toList();

        final List<Train> trains = new ArrayList<>();
        for (final NeTExEntityService.NeTExDatedServiceJourney dsj : slice.datedServiceJourneys()) {
            final NeTExCompositionService.NeTExDatedVehicleJourney composition = compositionsByJourney.get(dsj.id());
            if (composition != null) {
                trains.add(compositionWriter.buildTrain(composition));
                matched.add(dsj.id());
            }
        }

        final CompositeFrame compositeFrame = frame(localId, generationTimestamp,
                buildValidityConditions(localId, dates, generationTimestamp))
                .withFrames(new Frames_RelStructure()
                        .withCommonFrame(
                                FACTORY.createResourceFrame(buildLineResourceFrame(localId, trains)),
                                FACTORY.createServiceFrame(buildLineServiceFrame(localId, slice)),
                                FACTORY.createTimetableFrame(buildTimetableFrame(localId,
                                        slice.serviceJourneys(), slice.datedServiceJourneys(),
                                        compositionsByJourney))));

        return delivery(generationTimestamp, slice.line().name(), compositeFrame);
    }

    /**
     * Codespaces and frame defaults are repeated in every file so each file is
     * self-describing; Codespace ids are plain tokens, not versioned NeTEx ids, so
     * repeating them does not clash.
     */
    private CompositeFrame frame(final String localId, final ZonedDateTime generationTimestamp,
            final ValidityConditions_RelStructure validityConditions) {
        return new CompositeFrame()
                .withId("FTR:CompositeFrame:" + localId)
                .withVersion("1")
                .withCreated(generationTimestamp.toLocalDateTime())
                .withValidityConditions(validityConditions)
                .withCodespaces(new Codespaces_RelStructure()
                        .withCodespaceRefOrCodespace(
                                new Codespace()
                                        .withId("ftr")
                                        .withXmlns("FTR")
                                        .withXmlnsUrl("https://rata.digitraffic.fi"),
                                // FSR is PETI's/Fintraffic's codespace; we only reference it. PETI's own
                                // NeTEx export declares no XmlnsUrl for FSR.
                                new Codespace()
                                        .withId("fsr")
                                        .withXmlns("FSR")))
                .withFrameDefaults(new VersionFrameDefaultsStructure()
                        .withDefaultLocale(new LocaleStructure()
                                .withTimeZone("Europe/Helsinki")
                                .withDefaultLanguage("fi")));
    }

    private PublicationDeliveryStructure delivery(final ZonedDateTime generationTimestamp,
            final String description, final CompositeFrame compositeFrame) {
        return new PublicationDeliveryStructure()
                .withVersion(VERSION)
                .withPublicationTimestamp(generationTimestamp.toLocalDateTime())
                .withParticipantRef("FTR")
                .withDescription(new MultilingualString().withValue(description))
                .withDataObjects(new PublicationDeliveryStructure.DataObjects()
                        .withCompositeFrameOrCommonFrame(FACTORY.createCompositeFrame(compositeFrame)));
    }

    /** "FTR:Line:IC-1" -> "IC-1". */
    private static String localId(final String netexId) {
        final int lastColon = netexId.lastIndexOf(':');
        return lastColon < 0 ? netexId : netexId.substring(lastColon + 1);
    }

    /**
     * Temporal envelope for the delivery, derived from every dated element it
     * contains so it can never drift from the data it describes. The end is pushed
     * into the following morning because journeys on the last operating day run
     * past midnight. A delivery carrying no dated data falls back to its
     * publication date.
     */
    private ValidityConditions_RelStructure buildValidityConditions(final String localId,
            final Collection<LocalDate> dates,
            final ZonedDateTime publicationTimestamp) {
        final SortedSet<LocalDate> distinct = new TreeSet<>(dates);
        final LocalDate firstDay = distinct.isEmpty() ? publicationTimestamp.toLocalDate() : distinct.first();
        final LocalDate lastDay = distinct.isEmpty() ? publicationTimestamp.toLocalDate() : distinct.last();

        return new ValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(
                        FACTORY.createAvailabilityCondition(new AvailabilityCondition()
                                .withId("FTR:AvailabilityCondition:" + localId)
                                .withVersion("1")
                                .withFromDate(firstDay.atStartOfDay())
                                .withToDate(lastDay.plusDays(1).atTime(SERVICE_DAY_END))));
    }

    /**
     * Organisations and rolling stock types, both referenced from many Lines.
     */
    private ResourceFrame buildSharedResourceFrame(final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExCompositionService.NeTExVehicleType> vehicleTypes) {
        final OrganisationsInFrame_RelStructure organisations = new OrganisationsInFrame_RelStructure();
        for (final var op : operators) {
            organisations.getOrganisation_().add(FACTORY.createOperator(new Operator()
                    .withId(op.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(op.name()))
                    .withPrivateCode(new PrivateCodeStructure().withValue(op.privateCode()))
                    .withCompanyNumber(String.valueOf(op.companyNumber()))));
        }

        final ResourceFrame frame = new ResourceFrame()
                .withId("FTR:ResourceFrame:shared")
                .withVersion("1");
        if (!operators.isEmpty()) {
            frame.withOrganisations(organisations);
        }

        final List<VehicleType> netexVehicleTypes = compositionWriter.buildVehicleTypes(vehicleTypes);
        if (!netexVehicleTypes.isEmpty()) {
            final VehicleTypesInFrame_RelStructure vtStruct = new VehicleTypesInFrame_RelStructure();
            vtStruct.getCompoundTrainOrTrainOrVehicleType().addAll(netexVehicleTypes);
            frame.withVehicleTypes(vtStruct);
        }
        return frame;
    }

    /**
     * Train formations are specific to the journeys of one Line, so they live in
     * that Line's file rather than the common one.
     */
    private ResourceFrame buildLineResourceFrame(final String localId, final List<Train> trains) {
        final ResourceFrame frame = new ResourceFrame()
                .withId("FTR:ResourceFrame:" + localId)
                .withVersion("1");
        if (!trains.isEmpty()) {
            final VehicleTypesInFrame_RelStructure vtStruct = new VehicleTypesInFrame_RelStructure();
            vtStruct.getCompoundTrainOrTrainOrVehicleType().addAll(trains);
            frame.withVehicleTypes(vtStruct);
        }
        return frame;
    }

    /**
     * Cross-line content of the common file. The Nordic profile forbids Line,
     * Route and JourneyPattern here, so those live in the line files.
     */
    private ServiceFrame buildSharedServiceFrame(final NeTExStopsData stopsData) {
        final ServiceFrame frame = new ServiceFrame()
                .withId("FTR:ServiceFrame:shared")
                .withVersion("1");

        frame.withNetwork(new Network()
                .withId("FTR:Network:FIN")
                .withVersion("1")
                .withName(new MultilingualString().withValue("Finnish Railways"))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.RAIL));

        final DestinationDisplaysInFrame_RelStructure destDisplays = new DestinationDisplaysInFrame_RelStructure();
        for (final var dd : stopsData.getDestinationDisplays()) {
            destDisplays.getDestinationDisplay().add(new DestinationDisplay()
                    .withId(dd.id())
                    .withVersion("1")
                    .withFrontText(new MultilingualString().withValue(dd.frontText())));
        }
        frame.withDestinationDisplays(destDisplays);

        final ScheduledStopPointsInFrame_RelStructure stopPoints = new ScheduledStopPointsInFrame_RelStructure();
        for (final var stop : stopsData.getScheduledStopPoints()) {
            stopPoints.getScheduledStopPoint().add(new ScheduledStopPoint()
                    .withId(stop.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(stop.name()))
                    .withPrivateCode(new PrivateCodeStructure().withValue(stop.privateCode()))
                    .withLocation(new LocationStructure()
                            .withLatitude(stop.latitude())
                            .withLongitude(stop.longitude())));
        }
        frame.withScheduledStopPoints(stopPoints);

        final RoutePointsInFrame_RelStructure routePointsStructure = new RoutePointsInFrame_RelStructure();
        for (final var rp : stopsData.getRoutePoints()) {
            routePointsStructure.getRoutePoint().add(new RoutePoint()
                    .withId(rp.id())
                    .withVersion("1"));
        }
        frame.withRoutePoints(routePointsStructure);

        if (!stopsData.getStopAssignments().isEmpty()) {
            final StopAssignmentsInFrame_RelStructure assignments = new StopAssignmentsInFrame_RelStructure();
            for (final NeTExStopsData.NeTExStopAssignment a : stopsData.getStopAssignments()) {
                final PassengerStopAssignment psa = new PassengerStopAssignment()
                        .withId(a.id())
                        .withVersion("1")
                        .withScheduledStopPointRef(FACTORY.createScheduledStopPointRef(
                                new ScheduledStopPointRefStructure().withRef(a.scheduledStopPointRef())
                                        .withVersion("1")))
                        .withStopPlaceRef(FACTORY.createStopPlaceRef(
                                new StopPlaceRefStructure().withRef(a.stopPlaceRef())));
                if (a.quayRef() != null) {
                    psa.withQuayRef(FACTORY.createQuayRef(
                            new QuayRefStructure().withRef(a.quayRef())));
                }
                assignments.getStopAssignment().add(FACTORY.createPassengerStopAssignment(psa));
            }
            frame.withStopAssignments(assignments);
        }

        return frame;
    }

    /** The one Line of a line file, with the Routes and JourneyPatterns it owns. */
    private ServiceFrame buildLineServiceFrame(final String localId,
            final NeTExDatasetPartition.LineSlice slice) {
        final ServiceFrame frame = new ServiceFrame()
                .withId("FTR:ServiceFrame:" + localId)
                .withVersion("1");

        final var line = slice.line();
        frame.withLines(new LinesInFrame_RelStructure()
                .withLine_(FACTORY.createLine(new Line()
                        .withId(line.id())
                        .withVersion("1")
                        .withName(new MultilingualString().withValue(line.name()))
                        .withPublicCode(line.publicCode())
                        .withPrivateCode(new PrivateCodeStructure().withValue(line.privateCode()))
                        .withTransportMode(AllVehicleModesOfTransportEnumeration.RAIL)
                        .withOperatorRef(new OperatorRefStructure().withRef(line.operatorRef()))
                        // Cross-file references carry no version: the XSD keyref is keyed on
                        // (id, version), so omitting it keeps the constraint from being applied to
                        // entities that live in the common file.
                        .withRepresentedByGroupRef(
                                new GroupOfLinesRefStructure().withRef("FTR:Network:FIN")))));

        final RoutesInFrame_RelStructure routesStructure = new RoutesInFrame_RelStructure();
        for (final var route : slice.routes()) {
            final List<PointOnRoute> points = new ArrayList<>();
            int order = 1;
            for (final String ref : route.routePointRefs()) {
                points.add(new PointOnRoute()
                        .withId(route.id() + "-" + order)
                        .withVersion("1")
                        .withOrder(BigInteger.valueOf(order))
                        .withPointRef(FACTORY.createRoutePointRef(
                                new RoutePointRefStructure().withRef(ref))));
                order++;
            }
            routesStructure.getRoute_().add(FACTORY.createRoute(new Route()
                    .withId(route.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(route.name()))
                    .withShortName(new MultilingualString().withValue(route.name().replace(" - ", "-")))
                    .withLineRef(
                            FACTORY.createLineRef(new LineRefStructure().withRef(route.lineRef()).withVersion("1")))
                    .withPointsInSequence(new PointsOnRoute_RelStructure().withPointOnRoute(points))));
        }
        frame.withRoutes(routesStructure);

        final JourneyPatternsInFrame_RelStructure patternsStructure = new JourneyPatternsInFrame_RelStructure();
        for (final var pattern : slice.journeyPatterns()) {
            final List<StopPointInJourneyPattern> stops = new ArrayList<>();
            for (final var sp : pattern.stopPoints()) {
                final StopPointInJourneyPattern spijp = new StopPointInJourneyPattern()
                        .withId(idGenerator.stopPointInJourneyPatternId(pattern.id(), sp.order()))
                        .withVersion("1")
                        .withOrder(BigInteger.valueOf(sp.order()))
                        .withScheduledStopPointRef(FACTORY.createScheduledStopPointRef(
                                new ScheduledStopPointRefStructure().withRef(sp.scheduledStopPointRef())))
                        .withForBoarding(sp.forBoarding())
                        .withForAlighting(sp.forAlighting());
                if (sp.destinationDisplayRef() != null) {
                    spijp.withDestinationDisplayRef(
                            new DestinationDisplayRefStructure().withRef(sp.destinationDisplayRef()));
                }
                stops.add(spijp);
            }
            final PointsInJourneyPattern_RelStructure pointsInSequence = new PointsInJourneyPattern_RelStructure();
            pointsInSequence.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                    .addAll(stops);
            patternsStructure.getJourneyPattern_OrJourneyPatternView().add(
                    FACTORY.createJourneyPattern(new JourneyPattern()
                            .withId(pattern.id())
                            .withVersion("1")
                            .withRouteRef(new RouteRefStructure().withRef(pattern.routeRef()).withVersion("1"))
                            .withPointsInSequence(pointsInSequence)));
        }
        frame.withJourneyPatterns(patternsStructure);

        return frame;
    }

    /**
     * Calendar is expressed purely as OperatingDays referenced by
     * DatedServiceJourney. DayTypes/OperatingPeriods are deliberately not
     * emitted: a ServiceJourney may not carry both DayTypes and dated journeys
     * (SERVICE_JOURNEY_14), and unreferenced DayTypes are themselves a finding.
     */
    private ServiceCalendarFrame buildServiceCalendarFrame(final Collection<LocalDate> operatingDays) {
        final OperatingDaysInFrame_RelStructure operatingDaysStructure = new OperatingDaysInFrame_RelStructure();
        for (final LocalDate date : new TreeSet<>(operatingDays)) {
            operatingDaysStructure.getOperatingDay().add(new OperatingDay()
                    .withId(idGenerator.operatingDayId(date))
                    .withVersion("1")
                    .withCalendarDate(date.atStartOfDay()));
        }

        return new ServiceCalendarFrame()
                .withId("FTR:ServiceCalendarFrame:shared")
                .withVersion("1")
                .withOperatingDays(operatingDaysStructure);
    }

    private TimetableFrame buildTimetableFrame(final String localId,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            final List<NeTExEntityService.NeTExDatedServiceJourney> datedServiceJourneys,
            final Map<String, NeTExCompositionService.NeTExDatedVehicleJourney> compositionsByJourney) {
        final JourneysInFrame_RelStructure vehicleJourneys = new JourneysInFrame_RelStructure();
        final List<org.rutebanken.netex.model.Journey_VersionStructure> journeys = vehicleJourneys
                .getVehicleJourneyOrDatedVehicleJourneyOrNormalDatedVehicleJourney();
        serviceJourneys.stream().map(this::buildServiceJourney).forEach(journeys::add);
        datedServiceJourneys.stream()
                .map(dsj -> buildDatedServiceJourney(dsj, compositionsByJourney.get(dsj.id())))
                .forEach(journeys::add);

        return new TimetableFrame()
                .withId("FTR:TimetableFrame:" + localId)
                .withVersion("1")
                .withVehicleJourneys(vehicleJourneys);
    }

    /**
     * Dated production journey: the recurring ServiceJourney materialized for one
     * OperatingDay, carrying its train formation when one is known for that day.
     */
    private DatedServiceJourney buildDatedServiceJourney(final NeTExEntityService.NeTExDatedServiceJourney dsj,
            final NeTExCompositionService.NeTExDatedVehicleJourney composition) {
        final DatedServiceJourney journey = new DatedServiceJourney()
                .withId(dsj.id())
                .withVersion("1")
                .withJourneyRef(FACTORY.createServiceJourneyRef(
                        new ServiceJourneyRefStructure().withRef(dsj.serviceJourneyRef()).withVersion("1")))
                .withOperatingDayRef(new OperatingDayRefStructure()
                        .withRef(idGenerator.operatingDayId(dsj.operatingDay())));

        if (composition != null) {
            compositionWriter.applyComposition(journey, composition);
        }
        return journey;
    }

    /**
     * Builds the _FTR_shared_data.xml delivery: a ServiceCalendarFrame of
     * OperatingDays that the timetables and compositions reference via
     * OperatingDayRef.
     */
    public PublicationDeliveryStructure buildOperatingDaySharedData(final Collection<LocalDate> dates,
            final ZonedDateTime timestamp) {
        final Set<LocalDate> distinct = new TreeSet<>(dates);
        final OperatingDaysInFrame_RelStructure operatingDays = new OperatingDaysInFrame_RelStructure();
        for (final LocalDate date : distinct) {
            operatingDays.getOperatingDay().add(new OperatingDay()
                    .withId(idGenerator.operatingDayId(date))
                    .withVersion("1")
                    .withCalendarDate(date.atStartOfDay()));
        }

        final ServiceCalendarFrame calendarFrame = new ServiceCalendarFrame()
                .withId("FTR:ServiceCalendarFrame:operating-days")
                .withVersion("1")
                .withValidityConditions(buildValidityConditions("operating-days", distinct, timestamp))
                .withOperatingDays(operatingDays);

        final PublicationDeliveryStructure.DataObjects dataObjects = new PublicationDeliveryStructure.DataObjects()
                .withCompositeFrameOrCommonFrame(FACTORY.createServiceCalendarFrame(calendarFrame));

        return new PublicationDeliveryStructure()
                .withVersion(VERSION)
                .withPublicationTimestamp(timestamp.toLocalDateTime())
                .withParticipantRef("FTR")
                .withDescription(new MultilingualString().withValue("Finland rail shared data (operating days)"))
                .withDataObjects(dataObjects);
    }

    private ServiceJourney buildServiceJourney(final NeTExEntityService.NeTExServiceJourney sj) {
        final List<TimetabledPassingTime> passingTimes = sj.passingTimes().stream()
                .map(pt -> {
                    final TimetabledPassingTime tpt = new TimetabledPassingTime()
                            .withId(sj.id() + "-" + pt.order())
                            .withVersion("1")
                            .withPointInJourneyPatternRef(FACTORY.createStopPointInJourneyPatternRef(
                                    new StopPointInJourneyPatternRefStructure()
                                            .withRef(pt.stopPointInJourneyPatternRef())
                                            .withVersion("1")));
                    if (pt.arrivalTime() != null) {
                        final ParsedTime arrival = parseNeTExTime(pt.arrivalTime());
                        tpt.withArrivalTime(arrival.time);
                        if (arrival.dayOffset > 0) {
                            tpt.withArrivalDayOffset(BigInteger.valueOf(arrival.dayOffset));
                        }
                    }
                    if (pt.departureTime() != null) {
                        final ParsedTime departure = parseNeTExTime(pt.departureTime());
                        tpt.withDepartureTime(departure.time);
                        if (departure.dayOffset > 0) {
                            tpt.withDepartureDayOffset(BigInteger.valueOf(departure.dayOffset));
                        }
                    }
                    return tpt;
                })
                .toList();

        return new ServiceJourney()
                .withId(sj.id())
                .withVersion("1")
                .withName(new MultilingualString().withValue(sj.name()))
                .withPrivateCode(new PrivateCodeStructure().withValue(sj.privateCode()))
                .withJourneyPatternRef(FACTORY.createJourneyPatternRef(
                        new JourneyPatternRefStructure().withRef(sj.journeyPatternRef()).withVersion("1")))
                .withOperatorRef(new OperatorRefStructure().withRef(sj.operatorRef()))
                .withLineRef(FACTORY.createLineRef(new LineRefStructure().withRef(sj.lineRef()).withVersion("1")))
                .withPassingTimes(new TimetabledPassingTimes_RelStructure()
                        .withTimetabledPassingTime(passingTimes));
    }

    private String marshalToXml(final PublicationDeliveryStructure delivery) {
        try {
            final Marshaller marshaller = getJaxbContext().createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            final StringWriter writer = new StringWriter();
            marshaller.marshal(FACTORY.createPublicationDelivery(delivery), writer);
            return writer.toString();
        } catch (final JAXBException e) {
            throw new RuntimeException("Failed to marshal NeTEx XML", e);
        }
    }

    private byte[] zipXmlFiles(final Map<String, String> xmlByFileName) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (final Map.Entry<String, String> entry : xmlByFileName.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create NeTEx ZIP", e);
        }
    }

    private byte[] zipXml(final String xml, final String fileName) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create NeTEx ZIP", e);
        }
    }

    private ParsedTime parseNeTExTime(final String timeString) {
        final String[] parts = timeString.split(":");
        final int hours = Integer.parseInt(parts[0]);
        final int minutes = Integer.parseInt(parts[1]);
        final int seconds = Integer.parseInt(parts[2]);

        if (hours >= 24) {
            return new ParsedTime(LocalTime.of(hours - 24, minutes, seconds), 1);
        }
        return new ParsedTime(LocalTime.of(hours, minutes, seconds), 0);
    }

    private record ParsedTime(LocalTime time, int dayOffset) {
    }
}
