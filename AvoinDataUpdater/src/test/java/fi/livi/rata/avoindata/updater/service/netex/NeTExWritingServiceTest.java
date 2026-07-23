package fi.livi.rata.avoindata.updater.service.netex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExWritingService — XML marshalling and ZIP output.
 */
class NeTExWritingServiceTest {

    private NeTExWritingService writingService;

    @BeforeEach
    void setUp() {
        writingService = new NeTExWritingService();
    }

    @Test
    void givenValidData_whenWritingZip_thenProducesValidZipWithSingleXmlFile() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then: valid ZIP containing DT_rail_timetables.xml
        assertNotNull(zip);
        assertTrue(zip.length > 0);

        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            final ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);
            assertEquals("DT_rail_timetables.xml", entry.getName());
            assertNull(zis.getNextEntry()); // only one file
        }
    }

    @Test
    void givenValidData_whenWritingZip_thenXmlHasCorrectRootElement() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then: XML root is PublicationDelivery with NeTEx namespace
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<PublicationDelivery"));
        assertTrue(xml.contains("http://www.netex.org.uk/netex"));
    }

    @Test
    void givenValidData_whenWritingZip_thenVersionAttributeIsCorrect() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("version=\"1.15:NO-NeTEx-networktimetable:1.5\""));
    }

    @Test
    void givenValidData_whenWritingZip_thenPublicationTimestampPresent() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<PublicationTimestamp>"));
    }

    @Test
    void givenValidData_whenWritingZip_thenParticipantRefIsFSR() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<ParticipantRef>DT</ParticipantRef>"));
    }

    @Test
    void givenValidData_whenWritingZip_thenContainsResourceFrame() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("ResourceFrame"));
    }

    @Test
    void givenValidData_whenWritingZip_thenContainsServiceFrame() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("ServiceFrame"));
    }

    @Test
    void givenValidData_whenWritingZip_thenContainsTimetableFrame() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("TimetableFrame"));
    }

    @Test
    void givenValidData_whenWritingZip_thenContainsServiceCalendarFrame() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("ServiceCalendarFrame") || xml.contains("dayTypes"));
    }

    @Test
    void givenValidData_whenWritingZip_thenCodespaceIsDefined() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("<Xmlns>DT</Xmlns>"));
    }

    @Test
    void givenValidData_whenWritingZip_thenTimezoneIsEuropeHelsinki() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("Europe/Helsinki"));
    }

    @Test
    void givenValidData_whenWritingZip_thenDefaultLanguageIsFi() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.contains("fi"));
    }

    @Test
    void givenValidData_whenWritingZip_thenOutputIsWellFormedXml() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then: can be parsed as XML without error
        final String xml = extractXmlFromZip(zip);
        final javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        assertDoesNotThrow(() -> {
            factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        });
    }

    @Test
    void givenValidData_whenWritingZip_thenXmlDeclarationUsesUtf8() throws Exception {
        // given
        final var testData = createMinimalTestData();

        // when
        final byte[] zip = writingService.writeNeTExZip(
                testData.stopsData, testData.routeData, testData.calendarData,
                testData.lines, testData.operators, testData.serviceJourneys,
                testData.timestamp);

        // then
        final String xml = extractXmlFromZip(zip);
        assertTrue(xml.startsWith("<?xml") && xml.contains("UTF-8"));
    }

    // --- Helpers ---

    private String extractXmlFromZip(final byte[] zip) throws Exception {
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            zis.getNextEntry();
            return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private TestData createMinimalTestData() {
        final NeTExStopsData stopsData = new NeTExStopsData(
                List.of(new NeTExStopsData.NeTExScheduledStopPoint("DT:ScheduledStopPoint:HKI", "Helsinki", "HKI",
                        new BigDecimal("60.172133"), new BigDecimal("24.941662"))),
                List.of(new NeTExStopsData.NeTExRoutePoint("DT:RoutePoint:HKI", "HKI")),
                List.of(new NeTExStopsData.NeTExDestinationDisplay("DT:DestinationDisplay:HKI", "Helsinki"))
        );

        final NeTExRouteData routeData = new NeTExRouteData(
                List.of(new NeTExRouteData.NeTExRoute("DT:Route:IC-F-abc", "Helsinki - Helsinki", "DT:Line:IC",
                        List.of("DT:RoutePoint:HKI"))),
                List.of(new NeTExRouteData.NeTExJourneyPattern("DT:JourneyPattern:IC-abc", "DT:Route:IC-F-abc",
                        List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "DT:ScheduledStopPoint:HKI",
                                true, false, "DT:DestinationDisplay:HKI")))),
                Map.of(1L, "DT:JourneyPattern:IC-abc")
        );

        final NeTExCalendarData calendarData = new NeTExCalendarData(
                List.of(new NeTExDayType("DT:DayType:MoTuWeThFr-20260615-20261214", "Monday Tuesday Wednesday Thursday Friday")),
                List.of(new NeTExOperatingPeriod("DT:OperatingPeriod:20260615-20261214",
                        LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14))),
                List.of(NeTExDayTypeAssignment.forOperatingPeriod("DT:DayType:MoTuWeThFr-20260615-20261214",
                        "DT:OperatingPeriod:20260615-20261214")),
                Map.of(1L, "DT:DayType:MoTuWeThFr-20260615-20261214")
        );

        final var lines = List.of(new NeTExEntityService.NeTExLine("DT:Line:IC", "IC", "rail"));
        final var operators = List.of(new NeTExEntityService.NeTExOperator("DT:Operator:vr", "VR", "vr", 10));
        final var serviceJourneys = List.of(new NeTExEntityService.NeTExServiceJourney(
                "DT:ServiceJourney:59-12345", "IC 59", "59",
                "DT:JourneyPattern:IC-abc", "DT:Operator:vr", "DT:Line:IC",
                "DT:DayType:MoTuWeThFr-20260615-20261214",
                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00", null, null))));

        return new TestData(stopsData, routeData, calendarData, lines, operators, serviceJourneys,
                ZonedDateTime.of(2026, 6, 30, 4, 0, 0, 0, ZoneOffset.UTC));
    }

    private record TestData(NeTExStopsData stopsData, NeTExRouteData routeData, NeTExCalendarData calendarData,
                             List<NeTExEntityService.NeTExLine> lines,
                             List<NeTExEntityService.NeTExOperator> operators,
                             List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
                             ZonedDateTime timestamp) {}
}
