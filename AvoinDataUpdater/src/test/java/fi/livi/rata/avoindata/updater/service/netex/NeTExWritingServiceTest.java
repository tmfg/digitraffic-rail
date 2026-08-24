package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for NeTExWritingService — XML marshalling and ZIP output.
 */
class NeTExWritingServiceTest {

        private NeTExWritingService writingService;

        @BeforeEach
        void setUp() {
                writingService = new NeTExWritingService(new NeTExIdGenerator());
        }

        @Test
        void givenValidData_whenWritingZip_thenProducesValidZipWithSharedDataAndTimetables() throws Exception {
                // given
                final var testData = createMinimalTestData();

                // when
                final byte[] zip = writingService.writeNeTExZip(
                                testData.stopsData, testData.routeData, testData.calendarData,
                                testData.lines, testData.operators, testData.serviceJourneys,
                                testData.timestamp);

                // then: valid ZIP containing the common file and one file per Line
                assertNotNull(zip);
                assertTrue(zip.length > 0);

                final java.util.Set<String> names = new java.util.HashSet<>();
                try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                                names.add(entry.getName());
                        }
                }
                assertTrue(names.contains("_FTR_shared_data.xml"));
                assertTrue(names.contains("FTR_IC_Helsinki-Oulu.xml"));
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
                assertTrue(xml.contains("<ParticipantRef>FTR</ParticipantRef>"));
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
                final String xml = extractSharedXmlFromZip(zip);
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
                assertTrue(xml.contains("<Xmlns>FTR</Xmlns>"));
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
                final javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
                                .newInstance();
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

        @Test
        void givenValidData_whenWritingZip_thenFramesAreWrappedInCompositeFrame() throws Exception {
                // given
                final var testData = createMinimalTestData();

                // when
                final byte[] zip = writingService.writeNeTExZip(
                                testData.stopsData, testData.routeData, testData.calendarData,
                                testData.lines, testData.operators, testData.serviceJourneys,
                                testData.timestamp);

                // then
                final String xml = extractXmlFromZip(zip);
                assertTrue(xml.contains("<CompositeFrame"));
                assertTrue(xml.indexOf("<CompositeFrame") < xml.indexOf("<ResourceFrame"));
                assertTrue(xml.indexOf("<CompositeFrame") < xml.indexOf("<TimetableFrame"));
        }

        @Test
        void givenValidData_whenWritingZip_thenCompositeFrameDeclaresValidityCondition() throws Exception {
                // given
                final var testData = createMinimalTestData();

                // when
                final byte[] zip = writingService.writeNeTExZip(
                                testData.stopsData, testData.routeData, testData.calendarData,
                                testData.lines, testData.operators, testData.serviceJourneys,
                                testData.timestamp);

                // then
                final String xml = extractXmlFromZip(zip);
                assertTrue(xml.contains("<validityConditions>"));
                assertTrue(xml.contains("<AvailabilityCondition"));
        }

        @Test
        void givenDatedJourneys_whenWritingZip_thenValidityRangeSpansOperatingDays() throws Exception {
                // given
                final var testData = createMinimalTestData();
                final var dated = List.of(
                                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:59-20260701",
                                                "FTR:ServiceJourney:59-12345", LocalDate.of(2026, 7, 1)),
                                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:59-20260705",
                                                "FTR:ServiceJourney:59-12345", LocalDate.of(2026, 7, 5)));

                // when
                final byte[] zip = writingService.writeNeTExZip(
                                testData.stopsData, testData.routeData, testData.calendarData,
                                testData.lines, testData.operators, testData.serviceJourneys, dated,
                                testData.timestamp);

                // then: end is pushed past midnight to cover journeys of the last day
                final String xml = extractSharedXmlFromZip(zip);
                assertTrue(xml.contains("<FromDate>2026-07-01T00:00:00</FromDate>"));
                assertTrue(xml.contains("<ToDate>2026-07-06T04:00:00</ToDate>"));
        }

        @Test
        void givenDatedJourneys_whenWritingZip_thenNoDayTypesAreEmitted() throws Exception {
                // given
                final var testData = createMinimalTestData();
                final var dated = List.of(
                                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:59-20260701",
                                                "FTR:ServiceJourney:59-12345", LocalDate.of(2026, 7, 1)));

                // when
                final byte[] zip = writingService.writeNeTExZip(
                                testData.stopsData, testData.routeData, testData.calendarData,
                                testData.lines, testData.operators, testData.serviceJourneys, dated,
                                testData.timestamp);

                // then: calendar is expressed purely as OperatingDays (SERVICE_JOURNEY_14)
                final String shared = extractSharedXmlFromZip(zip);
                assertTrue(shared.contains("<OperatingDay"));
                assertFalse(shared.contains("<DayType"));
                assertFalse(shared.contains("<OperatingPeriod"));
                assertFalse(extractXmlFromZip(zip).contains("<dayTypes>"));
        }

        // --- Helpers ---

        private String extractXmlFromZip(final byte[] zip) throws Exception {
                return extractEntry(zip, name -> !name.startsWith("_"));
        }

        private String extractSharedXmlFromZip(final byte[] zip) throws Exception {
                return extractEntry(zip, name -> name.startsWith("_"));
        }

        private String extractEntry(final byte[] zip, final java.util.function.Predicate<String> match)
                        throws Exception {
                try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                                if (match.test(entry.getName())) {
                                        return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                }
                        }
                        throw new IllegalStateException("No matching entry in ZIP");
                }
        }

        private TestData createMinimalTestData() {
                final NeTExStopsData stopsData = new NeTExStopsData(
                                List.of(new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:HKI",
                                                "Helsinki", "HKI",
                                                new BigDecimal("60.172133"), new BigDecimal("24.941662"))),
                                List.of(new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:HKI", "HKI")),
                                List.of(new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:HKI",
                                                "Helsinki")));

                final NeTExRouteData routeData = new NeTExRouteData(
                                List.of(new NeTExRouteData.NeTExRoute("FTR:Route:IC-F-abc", "Helsinki - Helsinki",
                                                "FTR:Line:IC",
                                                List.of("FTR:RoutePoint:HKI"))),
                                List.of(new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:IC-abc",
                                                "FTR:Route:IC-F-abc",
                                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1,
                                                                "FTR:ScheduledStopPoint:HKI",
                                                                true, false, "FTR:DestinationDisplay:HKI")))),
                                Map.of(1L, "FTR:JourneyPattern:IC-abc"));

                final NeTExCalendarData calendarData = new NeTExCalendarData(
                                List.of(new NeTExDayType("FTR:DayType:MoTuWeThFr-20260615-20261214",
                                                "Monday Tuesday Wednesday Thursday Friday")),
                                List.of(new NeTExOperatingPeriod("FTR:OperatingPeriod:20260615-20261214",
                                                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14))),
                                List.of(NeTExDayTypeAssignment.forOperatingPeriod(
                                                "FTR:DayType:MoTuWeThFr-20260615-20261214",
                                                "FTR:OperatingPeriod:20260615-20261214")),
                                Map.of(1L, "FTR:DayType:MoTuWeThFr-20260615-20261214"));

                final var lines = List.of(new NeTExEntityService.NeTExLine("FTR:Line:IC", "Helsinki-Oulu", "IC",
                                "IC", "FTR:Operator:vr", "rail"));
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
                        ZonedDateTime timestamp) {
        }
}
