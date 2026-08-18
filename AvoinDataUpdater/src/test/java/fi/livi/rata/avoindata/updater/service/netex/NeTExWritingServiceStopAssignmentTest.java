package fi.livi.rata.avoindata.updater.service.netex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExWritingService — PassengerStopAssignment XML output and FSR codespace.
 */
class NeTExWritingServiceStopAssignmentTest {

    private NeTExWritingService writingService;

    @BeforeEach
    void setUp() {
        writingService = new NeTExWritingService(new NeTExIdGenerator());
    }

    @Test
    void givenStopsDataWithOneAssignment_whenWritingZip_thenXmlContainsPassengerStopAssignment() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:HKI",
                        "FTR:ScheduledStopPoint:HKI", "FSR:StopPlace:1", null)));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<PassengerStopAssignment"), "Should contain PassengerStopAssignment element");
        assertTrue(xml.contains("FTR:PassengerStopAssignment:HKI"), "Should contain assignment ID");
        assertTrue(xml.contains("FTR:ScheduledStopPoint:HKI"), "Should contain ScheduledStopPointRef");
        assertTrue(xml.contains("FSR:StopPlace:1"), "Should contain StopPlaceRef");
    }

    @Test
    void givenStopsDataWithEmptyAssignments_whenWritingZip_thenXmlDoesNotContainStopAssignments() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of());

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then
        final String xml = extractXmlFromZip(zip);
        assertFalse(xml.contains("<PassengerStopAssignment"), "Should NOT contain PassengerStopAssignment");
        assertFalse(xml.contains("<stopAssignments>"), "Should NOT contain empty stopAssignments wrapper");
    }

    @Test
    void givenAnyData_whenWritingZip_thenXmlContainsBothDtAndFsrCodespaces() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of());

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<Xmlns>FTR</Xmlns>"), "Should contain FTR codespace");
        assertTrue(xml.contains("<Xmlns>FSR</Xmlns>"), "Should contain FSR codespace");
    }

    @Test
    void givenMultipleAssignments_whenWritingZip_thenAllAppearAndXmlIsWellFormed() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:HKI",
                        "FTR:ScheduledStopPoint:HKI", "FSR:StopPlace:1", null),
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:TPE",
                        "FTR:ScheduledStopPoint:TPE", "FSR:StopPlace:2", null)));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then — both assignments are present
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("FTR:PassengerStopAssignment:HKI"), "Should contain first assignment");
        assertTrue(xml.contains("FTR:PassengerStopAssignment:TPE"), "Should contain second assignment");

        // and the XML is well-formed
        final javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assertDoesNotThrow(() -> {
            factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        });
    }

    // --- Pass 2b: QuayRef emission tests ---

    @Test
    void givenAssignmentWithQuayRef_whenWritingZip_thenXmlContainsQuayRefElement() throws Exception {
        // given — assignment with quayRef set
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:TRV-2",
                        "FTR:ScheduledStopPoint:TRV-2", "FSR:StopPlace:1", "FSR:Quay:10")));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then — XML contains QuayRef element with the correct ref
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("FSR:Quay:10"), "Should contain QuayRef value FSR:Quay:10");
        assertTrue(xml.contains("QuayRef"), "Should contain QuayRef element");
    }

    @Test
    void givenAssignmentWithNullQuayRef_whenWritingZip_thenXmlDoesNotContainQuayRef() throws Exception {
        // given — assignment with quayRef null (station-level only)
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:HKI",
                        "FTR:ScheduledStopPoint:HKI", "FSR:StopPlace:1", null)));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then — XML does NOT contain QuayRef but still has StopPlaceRef
        final String xml = extractXmlFromZip(zip);
        assertFalse(xml.contains("QuayRef"), "Should NOT contain QuayRef when quayRef is null");
        assertTrue(xml.contains("FSR:StopPlace:1"), "Should still contain StopPlaceRef");
    }

    @Test
    void givenAssignmentsWithQuayRefs_whenWritingZip_thenBothCodespacesPresent() throws Exception {
        // given — assignment with FSR StopPlaceRef and FSR QuayRef
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("FTR:PassengerStopAssignment:TRV-2",
                        "FTR:ScheduledStopPoint:TRV-2", "FSR:StopPlace:1", "FSR:Quay:10")));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then — both DT and FSR codespaces are declared
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<Xmlns>FTR</Xmlns>"), "Should contain FTR codespace");
        assertTrue(xml.contains("<Xmlns>FSR</Xmlns>"), "Should contain FSR codespace");
    }

    // --- Helpers ---

    private String extractXmlFromZip(final byte[] zip) throws Exception {
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            zis.getNextEntry();
            return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private TestData createTestDataWithAssignments(final List<NeTExStopsData.NeTExStopAssignment> assignments) {
        final NeTExStopsData stopsData = new NeTExStopsData(
                List.of(new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:HKI", "Helsinki", "HKI",
                        new BigDecimal("60.172133"), new BigDecimal("24.941662"))),
                List.of(new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:HKI", "HKI")),
                List.of(new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:HKI", "Helsinki")),
                assignments,
                assignments.size(),
                0);

        final NeTExRouteData routeData = new NeTExRouteData(
                List.of(new NeTExRouteData.NeTExRoute("FTR:Route:IC-F-abc", "Helsinki - Helsinki", "FTR:Line:IC",
                        List.of("FTR:RoutePoint:HKI"))),
                List.of(new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:IC-abc", "FTR:Route:IC-F-abc",
                        List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "FTR:ScheduledStopPoint:HKI",
                                true, false, "FTR:DestinationDisplay:HKI")))),
                Map.of(1L, "FTR:JourneyPattern:IC-abc"));

        final NeTExCalendarData calendarData = new NeTExCalendarData(
                List.of(new NeTExDayType("FTR:DayType:MoTuWeThFr-20260615-20261214", "Monday Tuesday Wednesday Thursday Friday")),
                List.of(new NeTExOperatingPeriod("FTR:OperatingPeriod:20260615-20261214",
                        LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14))),
                List.of(NeTExDayTypeAssignment.forOperatingPeriod("FTR:DayType:MoTuWeThFr-20260615-20261214",
                        "FTR:OperatingPeriod:20260615-20261214")),
                Map.of(1L, "FTR:DayType:MoTuWeThFr-20260615-20261214"));

        final var lines = List.of(new NeTExEntityService.NeTExLine("FTR:Line:IC", "IC", "rail"));
        final var operators = List.of(new NeTExEntityService.NeTExOperator("FTR:Operator:vr", "VR", "vr", 10));
        final var serviceJourneys = List.of(new NeTExEntityService.NeTExServiceJourney(
                "FTR:ServiceJourney:59-12345", "IC 59", "59",
                "FTR:JourneyPattern:IC-abc", "FTR:Operator:vr", "FTR:Line:IC",
                "FTR:DayType:MoTuWeThFr-20260615-20261214",
                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00", null, null,
                        "FTR:JourneyPattern:IC-abc-1"))));

        return new TestData(stopsData, routeData, calendarData, lines, operators, serviceJourneys,
                ZonedDateTime.of(2026, 6, 30, 4, 0, 0, 0, ZoneOffset.UTC));
    }

    private record TestData(NeTExStopsData stopsData, NeTExRouteData routeData, NeTExCalendarData calendarData,
                            List<NeTExEntityService.NeTExLine> lines,
                            List<NeTExEntityService.NeTExOperator> operators,
                            List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
                            ZonedDateTime timestamp) {}
}
