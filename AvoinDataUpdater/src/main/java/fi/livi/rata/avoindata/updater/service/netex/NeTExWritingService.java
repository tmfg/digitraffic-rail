package fi.livi.rata.avoindata.updater.service.netex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.Codespace;
import org.rutebanken.netex.model.Codespaces_RelStructure;
import org.rutebanken.netex.model.DayOfWeekEnumeration;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DayTypeAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.DayTypeRefs_RelStructure;
import org.rutebanken.netex.model.DayTypesInFrame_RelStructure;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.DestinationDisplayRefStructure;
import org.rutebanken.netex.model.DestinationDisplaysInFrame_RelStructure;
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
import org.rutebanken.netex.model.OperatingPeriod;
import org.rutebanken.netex.model.OperatingPeriodRefStructure;
import org.rutebanken.netex.model.OperatingPeriodsInFrame_RelStructure;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.OperatorRefStructure;
import org.rutebanken.netex.model.OrganisationsInFrame_RelStructure;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointOnRoute;
import org.rutebanken.netex.model.PointsInJourneyPattern_RelStructure;
import org.rutebanken.netex.model.PointsOnRoute_RelStructure;
import org.rutebanken.netex.model.PrivateCodeStructure;
import org.rutebanken.netex.model.PropertiesOfDay_RelStructure;
import org.rutebanken.netex.model.PropertyOfDay;
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
import org.rutebanken.netex.model.StopAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.StopPlaceRefStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.StopPointInJourneyPatternRefStructure;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.rutebanken.netex.model.TimetabledPassingTimes_RelStructure;
import org.rutebanken.netex.model.VersionFrameDefaultsStructure;
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

    private static final String VERSION = "1.15:NO-NeTEx-networktimetable:1.5";
    private static final ObjectFactory FACTORY = new ObjectFactory();

    private final NeTExIdGenerator idGenerator;

    public NeTExWritingService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
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
     * Assembles all NeTEx data into a PublicationDelivery XML document and writes
     * it to a ZIP.
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
        final PublicationDeliveryStructure delivery = buildPublicationDelivery(
                stopsData, routeData, calendarData, lines, operators, serviceJourneys, generationTimestamp);
        return marshalAndZip(delivery, "FTR_rail_timetables.xml");
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

    private PublicationDeliveryStructure buildPublicationDelivery(
            final NeTExStopsData stopsData,
            final NeTExRouteData routeData,
            final NeTExCalendarData calendarData,
            final List<NeTExEntityService.NeTExLine> lines,
            final List<NeTExEntityService.NeTExOperator> operators,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            final ZonedDateTime generationTimestamp) {

        final ResourceFrame resourceFrame = buildResourceFrame(operators);
        final ServiceFrame serviceFrame = buildServiceFrame(stopsData, routeData, lines);
        final ServiceCalendarFrame calendarFrame = buildServiceCalendarFrame(calendarData);
        final TimetableFrame timetableFrame = buildTimetableFrame(serviceJourneys);

        final PublicationDeliveryStructure.DataObjects dataObjects = new PublicationDeliveryStructure.DataObjects()
                .withCompositeFrameOrCommonFrame(
                        FACTORY.createResourceFrame(resourceFrame),
                        FACTORY.createServiceFrame(serviceFrame),
                        FACTORY.createServiceCalendarFrame(calendarFrame),
                        FACTORY.createTimetableFrame(timetableFrame));

        return new PublicationDeliveryStructure()
                .withVersion(VERSION)
                .withPublicationTimestamp(generationTimestamp.toLocalDateTime())
                .withParticipantRef("FTR")
                .withDataObjects(dataObjects);
    }

    private ResourceFrame buildResourceFrame(final List<NeTExEntityService.NeTExOperator> operators) {
        final List<Operator> netexOperators = operators.stream()
                .map(op -> new Operator()
                        .withId(op.id())
                        .withVersion("1")
                        .withName(new MultilingualString().withValue(op.name()))
                        .withPrivateCode(new PrivateCodeStructure().withValue(op.privateCode()))
                        .withCompanyNumber(String.valueOf(op.companyNumber())))
                .toList();

        final OrganisationsInFrame_RelStructure organisations = new OrganisationsInFrame_RelStructure();
        for (final Operator op : netexOperators) {
            organisations.getOrganisation_().add(FACTORY.createOperator(op));
        }

        return new ResourceFrame()
                .withId("FTR:ResourceFrame:1")
                .withVersion("1")
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
                                .withDefaultLanguage("fi")))
                .withOrganisations(organisations);
    }

    private ServiceFrame buildServiceFrame(final NeTExStopsData stopsData,
            final NeTExRouteData routeData,
            final List<NeTExEntityService.NeTExLine> lines) {
        final ServiceFrame frame = new ServiceFrame()
                .withId("FTR:ServiceFrame:1")
                .withVersion("1");

        // Network
        frame.withNetwork(new Network()
                .withId("FTR:Network:FIN")
                .withVersion("1")
                .withName(new MultilingualString().withValue("Finnish Railways"))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.RAIL));

        // Lines
        final LinesInFrame_RelStructure linesStructure = new LinesInFrame_RelStructure();
        for (final var line : lines) {
            linesStructure.getLine_().add(FACTORY.createLine(new Line()
                    .withId(line.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(line.publicCode()))
                    .withPublicCode(line.publicCode())
                    .withTransportMode(AllVehicleModesOfTransportEnumeration.RAIL)));
        }
        frame.withLines(linesStructure);

        // Destination displays
        final DestinationDisplaysInFrame_RelStructure destDisplays = new DestinationDisplaysInFrame_RelStructure();
        for (final var dd : stopsData.getDestinationDisplays()) {
            destDisplays.getDestinationDisplay().add(new DestinationDisplay()
                    .withId(dd.id())
                    .withVersion("1")
                    .withFrontText(new MultilingualString().withValue(dd.frontText())));
        }
        frame.withDestinationDisplays(destDisplays);

        // Scheduled stop points
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

        // Route points
        final RoutePointsInFrame_RelStructure routePointsStructure = new RoutePointsInFrame_RelStructure();
        for (final var rp : stopsData.getRoutePoints()) {
            routePointsStructure.getRoutePoint().add(new RoutePoint()
                    .withId(rp.id())
                    .withVersion("1"));
        }
        frame.withRoutePoints(routePointsStructure);

        // Routes
        final RoutesInFrame_RelStructure routesStructure = new RoutesInFrame_RelStructure();
        for (final var route : routeData.getRoutes()) {
            final List<PointOnRoute> points = new ArrayList<>();
            int order = 1;
            for (final String ref : route.routePointRefs()) {
                points.add(new PointOnRoute()
                        .withId(route.id() + "-" + order)
                        .withVersion("1")
                        .withOrder(BigInteger.valueOf(order))
                        .withPointRef(FACTORY.createRoutePointRef(
                                new RoutePointRefStructure().withRef(ref).withVersion("1"))));
                order++;
            }
            routesStructure.getRoute_().add(FACTORY.createRoute(new Route()
                    .withId(route.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(route.name()))
                    .withLineRef(FACTORY.createLineRef(new LineRefStructure().withRef(route.lineRef()).withVersion("1")))
                    .withPointsInSequence(new PointsOnRoute_RelStructure().withPointOnRoute(points))));
        }
        frame.withRoutes(routesStructure);

        // Journey patterns
        final JourneyPatternsInFrame_RelStructure patternsStructure = new JourneyPatternsInFrame_RelStructure();
        for (final var pattern : routeData.getJourneyPatterns()) {
            final List<StopPointInJourneyPattern> stops = new ArrayList<>();
            for (final var sp : pattern.stopPoints()) {
                final StopPointInJourneyPattern spijp = new StopPointInJourneyPattern()
                        .withId(idGenerator.stopPointInJourneyPatternId(pattern.id(), sp.order()))
                        .withVersion("1")
                        .withOrder(BigInteger.valueOf(sp.order()))
                        .withScheduledStopPointRef(FACTORY.createScheduledStopPointRef(
                                new ScheduledStopPointRefStructure().withRef(sp.scheduledStopPointRef()).withVersion("1")))
                        .withForBoarding(sp.forBoarding())
                        .withForAlighting(sp.forAlighting());
                if (sp.destinationDisplayRef() != null) {
                    spijp.withDestinationDisplayRef(
                            new DestinationDisplayRefStructure().withRef(sp.destinationDisplayRef()).withVersion("1"));
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

        // Passenger stop assignments (PETI station linkage)
        if (!stopsData.getStopAssignments().isEmpty()) {
            final StopAssignmentsInFrame_RelStructure assignments = new StopAssignmentsInFrame_RelStructure();
            for (final NeTExStopsData.NeTExStopAssignment a : stopsData.getStopAssignments()) {
                final PassengerStopAssignment psa = new PassengerStopAssignment()
                        .withId(a.id())
                        .withVersion("1")
                        .withScheduledStopPointRef(FACTORY.createScheduledStopPointRef(
                                new ScheduledStopPointRefStructure().withRef(a.scheduledStopPointRef()).withVersion("1")))
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

    private ServiceCalendarFrame buildServiceCalendarFrame(final NeTExCalendarData calendarData) {
        // Day types
        final DayTypesInFrame_RelStructure dayTypesStructure = new DayTypesInFrame_RelStructure();
        for (final var dt : calendarData.getDayTypes()) {
            dayTypesStructure.getDayType_().add(FACTORY.createDayType(new DayType()
                    .withId(dt.getId())
                    .withVersion("1")
                    .withProperties(new PropertiesOfDay_RelStructure()
                            .withPropertyOfDay(new PropertyOfDay()
                                    .withDaysOfWeek(parseDaysOfWeek(dt.getDaysOfWeek()))))));
        }

        // Operating periods
        final OperatingPeriodsInFrame_RelStructure periodsStructure = new OperatingPeriodsInFrame_RelStructure();
        for (final var op : calendarData.getOperatingPeriods()) {
            periodsStructure.getOperatingPeriodOrUicOperatingPeriod().add(new OperatingPeriod()
                    .withId(op.getId())
                    .withVersion("1")
                    .withFromDate(op.getFromDate().atStartOfDay())
                    .withToDate(op.getToDate().atStartOfDay()));
        }

        // Day type assignments
        final DayTypeAssignmentsInFrame_RelStructure assignmentsStructure = new DayTypeAssignmentsInFrame_RelStructure();
        int assignmentOrder = 1;
        for (final var dta : calendarData.getDayTypeAssignments()) {
            final DayTypeAssignment assignment = new DayTypeAssignment()
                    .withId("FTR:DayTypeAssignment:" + assignmentOrder)
                    .withVersion("1")
                    .withOrder(BigInteger.valueOf(assignmentOrder))
                    .withDayTypeRef(FACTORY.createDayTypeRef(
                            new DayTypeRefStructure().withRef(dta.getDayTypeId()).withVersion("1")));
            if (dta.getOperatingPeriodId() != null) {
                assignment.withOperatingPeriodRef(FACTORY.createOperatingPeriodRef(
                        new OperatingPeriodRefStructure().withRef(dta.getOperatingPeriodId()).withVersion("1")));
            }
            if (dta.getDate() != null) {
                assignment.withDate(dta.getDate().atStartOfDay());
            }
            assignmentsStructure.getDayTypeAssignment().add(assignment);
            assignmentOrder++;
        }

        return new ServiceCalendarFrame()
                .withId("FTR:ServiceCalendarFrame:1")
                .withVersion("1")
                .withDayTypes(dayTypesStructure)
                .withOperatingPeriods(periodsStructure)
                .withDayTypeAssignments(assignmentsStructure);
    }

    private TimetableFrame buildTimetableFrame(final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys) {
        final List<ServiceJourney> journeys = serviceJourneys.stream()
                .map(this::buildServiceJourney)
                .toList();

        final JourneysInFrame_RelStructure vehicleJourneys = new JourneysInFrame_RelStructure();
        vehicleJourneys.getVehicleJourneyOrDatedVehicleJourneyOrNormalDatedVehicleJourney().addAll(journeys);

        return new TimetableFrame()
                .withId("FTR:TimetableFrame:1")
                .withVersion("1")
                .withVehicleJourneys(vehicleJourneys);
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
                .withDayTypes(new DayTypeRefs_RelStructure()
                        .withDayTypeRef(FACTORY.createDayTypeRef(
                                new DayTypeRefStructure().withRef(sj.dayTypeRef()).withVersion("1"))))
                .withJourneyPatternRef(FACTORY.createJourneyPatternRef(
                        new JourneyPatternRefStructure().withRef(sj.journeyPatternRef()).withVersion("1")))
                .withOperatorRef(new OperatorRefStructure().withRef(sj.operatorRef()).withVersion("1"))
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

    private List<DayOfWeekEnumeration> parseDaysOfWeek(final String daysOfWeekString) {
        final List<DayOfWeekEnumeration> days = new ArrayList<>();
        if (daysOfWeekString.contains("Monday"))
            days.add(DayOfWeekEnumeration.MONDAY);
        if (daysOfWeekString.contains("Tuesday"))
            days.add(DayOfWeekEnumeration.TUESDAY);
        if (daysOfWeekString.contains("Wednesday"))
            days.add(DayOfWeekEnumeration.WEDNESDAY);
        if (daysOfWeekString.contains("Thursday"))
            days.add(DayOfWeekEnumeration.THURSDAY);
        if (daysOfWeekString.contains("Friday"))
            days.add(DayOfWeekEnumeration.FRIDAY);
        if (daysOfWeekString.contains("Saturday"))
            days.add(DayOfWeekEnumeration.SATURDAY);
        if (daysOfWeekString.contains("Sunday"))
            days.add(DayOfWeekEnumeration.SUNDAY);
        return days;
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
