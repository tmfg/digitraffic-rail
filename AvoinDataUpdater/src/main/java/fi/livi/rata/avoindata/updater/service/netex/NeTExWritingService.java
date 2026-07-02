package fi.livi.rata.avoindata.updater.service.netex;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.rutebanken.netex.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

/**
 * Serializes NeTEx domain objects to XML and produces the final ZIP output.
 * Uses Entur netex-java-model (JAXB) for type-safe marshalling.
 */
@Service
public class NeTExWritingService {

    private static final String VERSION = "1.15:NO-NeTEx-networktimetable:1.5";
    private static final ObjectFactory FACTORY = new ObjectFactory();

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
        final String xml = marshalToXml(delivery);
        return zipXml(xml);
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
                .withParticipantRef("DT")
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
                .withId("DT:ResourceFrame:1")
                .withVersion("1")
                .withCodespaces(new Codespaces_RelStructure()
                        .withCodespaceRefOrCodespace(new Codespace()
                                .withId("dt")
                                .withXmlns("DT")
                                .withXmlnsUrl("https://rata.digitraffic.fi")))
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
                .withId("DT:ServiceFrame:1")
                .withVersion("1");

        // Network
        frame.withNetwork(new Network()
                .withId("DT:Network:FIN")
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
                                new RoutePointRefStructure().withRef(ref))));
                order++;
            }
            routesStructure.getRoute_().add(FACTORY.createRoute(new Route()
                    .withId(route.id())
                    .withVersion("1")
                    .withName(new MultilingualString().withValue(route.name()))
                    .withLineRef(FACTORY.createLineRef(new LineRefStructure().withRef(route.lineRef())))
                    .withPointsInSequence(new PointsOnRoute_RelStructure().withPointOnRoute(points))));
        }
        frame.withRoutes(routesStructure);

        // Journey patterns
        final JourneyPatternsInFrame_RelStructure patternsStructure = new JourneyPatternsInFrame_RelStructure();
        for (final var pattern : routeData.getJourneyPatterns()) {
            final List<StopPointInJourneyPattern> stops = new ArrayList<>();
            for (final var sp : pattern.stopPoints()) {
                final StopPointInJourneyPattern spijp = new StopPointInJourneyPattern()
                        .withId(pattern.id() + "-" + sp.order())
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
                            .withRouteRef(new RouteRefStructure().withRef(pattern.routeRef()))
                            .withPointsInSequence(pointsInSequence)));
        }
        frame.withJourneyPatterns(patternsStructure);

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
                    .withId("DT:DayTypeAssignment:" + assignmentOrder)
                    .withVersion("1")
                    .withOrder(BigInteger.valueOf(assignmentOrder))
                    .withDayTypeRef(FACTORY.createDayTypeRef(
                            new DayTypeRefStructure().withRef(dta.getDayTypeId())));
            if (dta.getOperatingPeriodId() != null) {
                assignment.withOperatingPeriodRef(FACTORY.createOperatingPeriodRef(
                        new OperatingPeriodRefStructure().withRef(dta.getOperatingPeriodId())));
            }
            if (dta.getDate() != null) {
                assignment.withDate(dta.getDate().atStartOfDay());
            }
            assignmentsStructure.getDayTypeAssignment().add(assignment);
            assignmentOrder++;
        }

        return new ServiceCalendarFrame()
                .withId("DT:ServiceCalendarFrame:1")
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
                .withId("DT:TimetableFrame:1")
                .withVersion("1")
                .withVehicleJourneys(vehicleJourneys);
    }

    private ServiceJourney buildServiceJourney(final NeTExEntityService.NeTExServiceJourney sj) {
        final List<TimetabledPassingTime> passingTimes = sj.passingTimes().stream()
                .map(pt -> {
                    final TimetabledPassingTime tpt = new TimetabledPassingTime();
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
                                new DayTypeRefStructure().withRef(sj.dayTypeRef()))))
                .withJourneyPatternRef(FACTORY.createJourneyPatternRef(
                        new JourneyPatternRefStructure().withRef(sj.journeyPatternRef())))
                .withOperatorRef(new OperatorRefStructure().withRef(sj.operatorRef()))
                .withLineRef(FACTORY.createLineRef(new LineRefStructure().withRef(sj.lineRef())))
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

    private byte[] zipXml(final String xml) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("FIN_rail_timetable.xml"));
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
