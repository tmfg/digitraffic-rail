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
        writingService = new NeTExWritingService();
    }

    @Test
    void givenStopsDataWithOneAssignment_whenWritingZip_thenXmlContainsPassengerStopAssignment() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("DT:PassengerStopAssignment:HKI",
                        "DT:ScheduledStopPoint:HKI", "FSR:StopPlace:1")));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<PassengerStopAssignment"), "Should contain PassengerStopAssignment element");
        assertTrue(xml.contains("DT:PassengerStopAssignment:HKI"), "Should contain assignment ID");
        assertTrue(xml.contains("DT:ScheduledStopPoint:HKI"), "Should contain ScheduledStopPointRef");
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
        assertTrue(xml.contains("<Xmlns>DT</Xmlns>"), "Should contain DT codespace");
        assertTrue(xml.contains("<Xmlns>FSR</Xmlns>"), "Should contain FSR codespace");
    }

    @Test
    void givenMultipleAssignments_whenWritingZip_thenAllAppearAndXmlIsWellFormed() throws Exception {
        // given
        final var testData = createTestDataWithAssignments(List.of(
                new NeTExStopsData.NeTExStopAssignment("DT:PassengerStopAssignment:HKI",
                        "DT:ScheduledStopPoint:HKI", "FSR:StopPlace:1"),
                new NeTExStopsData.NeTExStopAssignment("DT:PassengerStopAssignment:TPE",
                        "DT:ScheduledStopPoint:TPE", "FSR:StopPlace:2")));

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData(), testData.routeData(), testData.calendarData(),
                testData.lines(), testData.operators(), testData.serviceJourneys(),
                testData.timestamp());

        // then — both assignments are present
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("DT:PassengerStopAssignment:HKI"), "Should contain first assignment");
        assertTrue(xml.contains("DT:PassengerStopAssignment:TPE"), "Should contain second assignment");

        // and the XML is well-formed
        final javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assertDoesNotThrow(() -> {
            factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        });
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
                List.of(new NeTExStopsData.NeTExScheduledStopPoint("DT:ScheduledStopPoint:HKI", "Helsinki", "HKI",
                        new BigDecimal("60.172133"), new BigDecimal("24.941662"))),
                List.of(new NeTExStopsData.NeTExRoutePoint("DT:RoutePoint:HKI", "HKI")),
                List.of(new NeTExStopsData.NeTExDestinationDisplay("DT:DestinationDisplay:HKI", "Helsinki")),
                assignments,
                assignments.size(),
                0);

        final NeTExRouteData routeData = new NeTExRouteData(
                List.of(new NeTExRouteData.NeTExRoute("DT:Route:IC-F-abc", "Helsinki - Helsinki", "DT:Line:IC",
                        List.of("DT:RoutePoint:HKI"))),
                List.of(new NeTExRouteData.NeTExJourneyPattern("DT:JourneyPattern:IC-abc", "DT:Route:IC-F-abc",
                        List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "DT:ScheduledStopPoint:HKI",
                                true, false, "DT:DestinationDisplay:HKI")))),
                Map.of(1L, "DT:JourneyPattern:IC-abc"));

        final NeTExCalendarData calendarData = new NeTExCalendarData(
                List.of(new NeTExDayType("DT:DayType:MoTuWeThFr-20260615-20261214", "Monday Tuesday Wednesday Thursday Friday")),
                List.of(new NeTExOperatingPeriod("DT:OperatingPeriod:20260615-20261214",
                        LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14))),
                List.of(NeTExDayTypeAssignment.forOperatingPeriod("DT:DayType:MoTuWeThFr-20260615-20261214",
                        "DT:OperatingPeriod:20260615-20261214")),
                Map.of(1L, "DT:DayType:MoTuWeThFr-20260615-20261214"));

        final var lines = List.of(new NeTExEntityService.NeTExLine("DT:Line:IC", "IC", "rail"));
        final var operators = List.of(new NeTExEntityService.NeTExOperator("DT:Operator:vr", "VR", "vr", 10));
        final var serviceJourneys = List.of(new NeTExEntityService.NeTExServiceJourney(
                "DT:ServiceJourney:59-12345", "IC 59", "59",
                "DT:JourneyPattern:IC-abc", "DT:Operator:vr", "DT:Line:IC",
                "DT:DayType:MoTuWeThFr-20260615-20261214",
                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00"))));

        return new TestData(stopsData, routeData, calendarData, lines, operators, serviceJourneys,
                ZonedDateTime.of(2026, 6, 30, 4, 0, 0, 0, ZoneOffset.UTC));
    }

    private record TestData(NeTExStopsData stopsData, NeTExRouteData routeData, NeTExCalendarData calendarData,
                            List<NeTExEntityService.NeTExLine> lines,
                            List<NeTExEntityService.NeTExOperator> operators,
                            List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
                            ZonedDateTime timestamp) {}
}
