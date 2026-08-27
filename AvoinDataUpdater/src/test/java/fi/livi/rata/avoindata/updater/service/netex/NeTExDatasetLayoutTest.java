package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.PublicationDeliveryStructure;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * Dataset-level invariants of the per-Line split: file scoping rules of the
 * Nordic profile, and id uniqueness across the files of the dataset.
 */
class NeTExDatasetLayoutTest {

        private static final Pattern NETEX_ID = Pattern.compile("\\bid=\"(FTR:[^\"]+)\"");

        private NeTExWritingService writingService;

        @BeforeEach
        void setUp() {
                writingService = new NeTExWritingService(new NeTExIdGenerator());
        }

        @Test
        void givenTwoLines_whenBuildingDataset_thenOneFilePerLinePlusSharedFile() throws Exception {
                final Map<String, String> files = build();

                assertEquals(Set.of("_FTR_shared_data.xml", "FTR_IC-1_Helsinki-Oulu.xml", "FTR_Z_Helsinki-Lahti.xml"),
                                files.keySet());
        }

        @Test
        void givenDataset_whenBuildingDataset_thenNoNeTExIdAppearsInMoreThanOneFile() throws Exception {
                final Map<String, String> files = build();

                final Map<String, String> owningFile = new LinkedHashMap<>();
                final List<String> duplicates = new ArrayList<>();
                for (final var entry : files.entrySet()) {
                        final Set<String> seenInThisFile = new HashSet<>();
                        for (final String id : declaredIds(entry.getValue())) {
                                if (!seenInThisFile.add(id)) {
                                        duplicates.add(id + " twice in " + entry.getKey());
                                        continue;
                                }
                                final String previous = owningFile.putIfAbsent(id, entry.getKey());
                                if (previous != null && !previous.equals(entry.getKey())) {
                                        duplicates.add(id + " in " + previous + " and " + entry.getKey());
                                }
                        }
                }

                assertTrue(duplicates.isEmpty(), "Duplicated NeTEx ids: " + duplicates);
        }

        @Test
        void givenTrackQualifiedPatternsEndingAtSameTrack_whenBuildingDataset_thenStopPointIdsStayUnique() {
                // given: two patterns of one Line differing only before the final stop,
                // both ending at HKI track 8 — the shape that collided in production
                final NeTExIdGenerator ids = new NeTExIdGenerator();
                final String viaHameenlinna = ids.journeyPatternId("IC-1", "OL-1_HM-1_HKI-8");
                final String direct = ids.journeyPatternId("IC-1", "OL-1_HKI-8");

                final NeTExRouteData routes = new NeTExRouteData(
                                List.of(new NeTExRouteData.NeTExRoute("FTR:Route:IC-1-a", "OL - HKI", "FTR:Line:IC-1",
                                                List.of("FTR:RoutePoint:HKI", "FTR:RoutePoint:OL"))),
                                List.of(journeyPattern(viaHameenlinna), journeyPattern(direct)),
                                Map.of());

                final var journeys = List.of(
                                serviceJourney("FTR:ServiceJourney:1", viaHameenlinna, ids),
                                serviceJourney("FTR:ServiceJourney:2", direct, ids));

                // when
                final Map<String, String> files = marshal(writingService.buildDataset(
                                stopsData(), routes, lines(), operators(), journeys,
                                NeTExCalendarService.NeTExCalendarData.empty(),
                                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC)));

                // then
                final String lineFile = files.get("FTR_IC-1_Helsinki-Oulu.xml");
                final List<String> declared = declaredIds(lineFile).stream()
                                .filter(id -> id.startsWith("FTR:StopPointInJourneyPattern:"))
                                .toList();
                assertEquals(declared.size(), new HashSet<>(declared).size(),
                                "StopPointInJourneyPattern ids must be unique: " + declared);
        }

        @Test
        void givenRouteEndingAtTrack_whenBuildingDataset_thenPointOnRouteIdsDoNotCollide() {
                // given: one route ends at HKI track 2 while another reaches HKI untracked,
                // whose second PointOnRoute then took the first route's id — the shape that
                // collided in production
                final NeTExIdGenerator ids = new NeTExIdGenerator();
                final String untracked = ids.routeId("IC-1", "OL_HKI");
                final String trackTwo = ids.routeId("IC-1", "OL_HKI-2");
                final String pattern = ids.journeyPatternId("IC-1", "OL_HKI");

                final NeTExRouteData routes = new NeTExRouteData(
                                List.of(new NeTExRouteData.NeTExRoute(untracked, "OL - HKI", "FTR:Line:IC-1",
                                                List.of("FTR:RoutePoint:OL", "FTR:RoutePoint:HKI")),
                                                new NeTExRouteData.NeTExRoute(trackTwo, "OL - HKI", "FTR:Line:IC-1",
                                                                List.of("FTR:RoutePoint:OL", "FTR:RoutePoint:HKI"))),
                                List.of(journeyPattern(pattern, untracked)),
                                Map.of());

                final var journeys = List.of(serviceJourney("FTR:ServiceJourney:1", pattern, ids));

                // when
                final Map<String, String> files = marshal(writingService.buildDataset(
                                stopsData(), routes, lines(), operators(), journeys,
                                NeTExCalendarService.NeTExCalendarData.empty(),
                                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC)));

                // then
                final List<String> declared = declaredIds(files.get("FTR_IC-1_Helsinki-Oulu.xml"));
                assertEquals(declared.size(), new HashSet<>(declared).size(),
                                "ids must be unique within a line file");
        }

        private static NeTExRouteData.NeTExJourneyPattern journeyPattern(final String patternId) {
                return journeyPattern(patternId, "FTR:Route:IC-1-a");
        }

        private static NeTExRouteData.NeTExJourneyPattern journeyPattern(final String patternId,
                        final String routeRef) {
                return new NeTExRouteData.NeTExJourneyPattern(patternId, routeRef,
                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "FTR:ScheduledStopPoint:OL",
                                                true, false, "FTR:DestinationDisplay:HKI"),
                                                new NeTExRouteData.NeTExStopPointInPattern(2,
                                                                "FTR:ScheduledStopPoint:HKI",
                                                                false, true, null)));
        }

        private static NeTExEntityService.NeTExServiceJourney serviceJourney(final String id,
                        final String patternId, final NeTExIdGenerator ids) {
                return new NeTExEntityService.NeTExServiceJourney(id, "IC 1", "1", patternId,
                                "FTR:Operator:vr", "FTR:Line:IC-1",
                                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00", "OL", null,
                                                ids.stopPointInJourneyPatternId(patternId, 1)),
                                                new NeTExEntityService.NeTExPassingTime(2, "11:30:00", null, "HKI",
                                                                null,
                                                                ids.stopPointInJourneyPatternId(patternId, 2))));
        }

        @Test
        void givenCommonFile_whenBuildingDataset_thenItCarriesNoLineRouteJourneyPatternOrTimetableFrame()
                        throws Exception {
                final String shared = build().get("_FTR_shared_data.xml");

                assertFalse(shared.contains("<TimetableFrame"), "TimetableFrame is illegal in a common file");
                assertFalse(shared.contains("<Line "), "Line is illegal in a common file ServiceFrame");
                assertFalse(shared.contains("<Route "), "Route is illegal in a common file ServiceFrame");
                assertFalse(shared.contains("<JourneyPattern "),
                                "JourneyPattern is illegal in a common file ServiceFrame");
        }

        @Test
        void givenLineFile_whenBuildingDataset_thenItCarriesNoResourceFrameAndOnlyItsOwnLine() throws Exception {
                final String lineFile = build().get("FTR_IC-1_Helsinki-Oulu.xml");

                // organisations are cross-line, so they live in the common file only
                assertEquals(0, countOccurrences(lineFile, "<ResourceFrame"));
                assertEquals(1, countOccurrences(lineFile, "<Line "));
                assertTrue(lineFile.contains("id=\"FTR:Line:IC-1\""));
                assertFalse(lineFile.contains("id=\"FTR:Line:Z\""), "A line file must not carry another Line");
        }

        @Test
        void givenEveryFile_whenBuildingDataset_thenEachDeclaresValidityConditions() throws Exception {
                build().forEach((fileName, xml) -> assertTrue(xml.contains("<validityConditions>"),
                                fileName + " is missing validityConditions"));
        }

        @Test
        void givenLineFile_whenBuildingDataset_thenValidityIsScopedToThatLinesDates() throws Exception {
                final Map<String, String> files = build();

                // IC-1 runs the two Thursdays, Z on 2026-08-22 only.
                assertTrue(files.get("FTR_IC-1_Helsinki-Oulu.xml")
                                .contains("<FromDate>2026-08-20T00:00:00</FromDate>"));
                assertTrue(files.get("FTR_IC-1_Helsinki-Oulu.xml").contains("<ToDate>2026-08-28T04:00:00</ToDate>"));
                assertTrue(files.get("FTR_Z_Helsinki-Lahti.xml").contains("<FromDate>2026-08-22T00:00:00</FromDate>"));
                assertTrue(files.get("FTR_Z_Helsinki-Lahti.xml").contains("<ToDate>2026-08-23T04:00:00</ToDate>"));
        }

        @Test
        void givenEveryFile_whenBuildingDataset_thenCodespacesAndFrameDefaultsAreSelfDescribing() throws Exception {
                build().forEach((fileName, xml) -> {
                        assertTrue(xml.contains("<Xmlns>FTR</Xmlns>"), fileName + " is missing codespaces");
                        assertTrue(xml.contains("Europe/Helsinki"), fileName + " is missing frame defaults");
                });
        }

        @Test
        void givenCalendar_whenBuildingDataset_thenDayTypesAreSharedAndReferencedPerLine() {
                // when
                final Map<String, String> files = marshal(writingService.buildDataset(
                                stopsData(), routeData(), lines(), operators(),
                                serviceJourneys(), calendar(),
                                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC)));

                // then
                final String shared = files.get("_FTR_shared_data.xml");
                final String ic1 = files.get("FTR_IC-1_Helsinki-Oulu.xml");
                final String z = files.get("FTR_Z_Helsinki-Lahti.xml");

                final String dayType1 = new NeTExIdGenerator()
                                .dayTypeId(hashOf(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 27)));
                final String dayType2 = new NeTExIdGenerator().dayTypeId(hashOf(LocalDate.of(2026, 8, 22)));

                assertTrue(shared.contains("id=\"" + dayType1 + "\""), "DayType belongs to the common file");
                assertFalse(ic1.contains("id=\"" + dayType1 + "\""), "DayType must not repeat in a line file");
                assertTrue(ic1.contains("ref=\"" + dayType1 + "\""), "journey must reference its DayType");
                assertTrue(z.contains("ref=\"" + dayType2 + "\""));
                assertFalse(ic1.contains("<DatedServiceJourney"), "DayTypes replace the dated journeys");
                // the weekly journey collapses to a period, the single-date one enumerates
                assertTrue(shared.contains("<OperatingPeriod"));
                assertTrue(shared.contains("<DaysOfWeek>Thursday</DaysOfWeek>"));
        }

        /** Mirrors generation: the calendar is built from resolved (train, day) pairs. */
        private static NeTExCalendarService.NeTExCalendarData calendar() {
                return new NeTExCalendarService(new NeTExIdGenerator()).createCalendarData(Map.of(
                                new TrainId(1L, LocalDate.of(2026, 8, 20)), "FTR:ServiceJourney:1",
                                new TrainId(1L, LocalDate.of(2026, 8, 27)), "FTR:ServiceJourney:1",
                                new TrainId(2L, LocalDate.of(2026, 8, 22)), "FTR:ServiceJourney:2"));
        }

        private static String hashOf(final LocalDate... dates) {
                final Map<TrainId, String> refs = new LinkedHashMap<>();
                for (final LocalDate date : dates) {
                        refs.put(new TrainId(9L, date), "FTR:ServiceJourney:probe");
                }
                final var calendar = new NeTExCalendarService(new NeTExIdGenerator()).createCalendarData(refs);
                final String id = calendar.dayTypeRefByServiceJourney().get("FTR:ServiceJourney:probe");
                return id.substring(id.lastIndexOf(':') + 1);
        }

        // --- Helpers ---

        private Map<String, String> marshal(final Map<String, PublicationDeliveryStructure> dataset) {
                final byte[] zip = writingService.marshalAndZip(dataset);
                final Map<String, String> files = new LinkedHashMap<>();
                try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                                files.put(entry.getName(), new String(zis.readAllBytes(),
                                                java.nio.charset.StandardCharsets.UTF_8));
                        }
                } catch (final Exception e) {
                        throw new RuntimeException("Failed to read dataset", e);
                }
                return files;
        }

        private Map<String, String> build() throws Exception {
                final byte[] zip = writingService.writeNeTExZip(
                                stopsData(), routeData(), lines(), operators(),
                                serviceJourneys(), calendar(),
                                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC));

                if (System.getProperty("netex.dump") != null) {
                        NeTExSampleDump.dump(zip, System.getProperty("netex.dump"));
                }

                final Map<String, String> files = new LinkedHashMap<>();
                try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                                files.put(entry.getName(), new String(zis.readAllBytes(),
                                                java.nio.charset.StandardCharsets.UTF_8));
                        }
                }
                return files;
        }

        /** Every declared id in document order — a Set here would hide duplicates. */
        private static List<String> declaredIds(final String xml) {
                final List<String> ids = new ArrayList<>();
                final Matcher matcher = NETEX_ID.matcher(xml);
                while (matcher.find()) {
                        ids.add(matcher.group(1));
                }
                return ids;
        }

        private static int countOccurrences(final String haystack, final String needle) {
                int count = 0;
                int index = haystack.indexOf(needle);
                while (index >= 0) {
                        count++;
                        index = haystack.indexOf(needle, index + needle.length());
                }
                return count;
        }

        private static List<NeTExEntityService.NeTExLine> lines() {
                return List.of(
                                new NeTExEntityService.NeTExLine("FTR:Line:IC-1", "Helsinki-Oulu", "IC 1", "IC-1",
                                                "FTR:Operator:vr", "rail"),
                                new NeTExEntityService.NeTExLine("FTR:Line:Z", "Helsinki-Lahti", "Z", "Z",
                                                "FTR:Operator:vr", "rail"));
        }

        private static List<NeTExEntityService.NeTExOperator> operators() {
                return List.of(new NeTExEntityService.NeTExOperator("FTR:Operator:vr", "VR", "vr", 10));
        }

        private static NeTExStopsData stopsData() {
                return new NeTExStopsData(
                                List.of(new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:HKI",
                                                "Helsinki", "HKI",
                                                new BigDecimal("60.172133"), new BigDecimal("24.941662")),
                                                new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:OL",
                                                                "Oulu", "OL",
                                                                new BigDecimal("65.011153"),
                                                                new BigDecimal("25.470834"))),
                                List.of(new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:HKI", "HKI"),
                                                new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:OL", "OL")),
                                List.of(new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:HKI",
                                                "Helsinki"),
                                                new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:OL",
                                                                "Oulu")));
        }

        private static NeTExRouteData routeData() {
                return new NeTExRouteData(
                                List.of(new NeTExRouteData.NeTExRoute("FTR:Route:IC-1-a", "HKI - OL", "FTR:Line:IC-1",
                                                List.of("FTR:RoutePoint:HKI", "FTR:RoutePoint:OL")),
                                                new NeTExRouteData.NeTExRoute("FTR:Route:Z-a", "HKI - OL", "FTR:Line:Z",
                                                                List.of("FTR:RoutePoint:HKI", "FTR:RoutePoint:OL"))),
                                List.of(new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:IC-1-a",
                                                "FTR:Route:IC-1-a",
                                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1,
                                                                "FTR:ScheduledStopPoint:HKI",
                                                                true, false, "FTR:DestinationDisplay:OL"),
                                                                new NeTExRouteData.NeTExStopPointInPattern(2,
                                                                                "FTR:ScheduledStopPoint:OL",
                                                                                false, true, null))),
                                                new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:Z-a",
                                                                "FTR:Route:Z-a",
                                                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1,
                                                                                "FTR:ScheduledStopPoint:HKI",
                                                                                true, false,
                                                                                "FTR:DestinationDisplay:OL"),
                                                                                new NeTExRouteData.NeTExStopPointInPattern(
                                                                                                2,
                                                                                                "FTR:ScheduledStopPoint:OL",
                                                                                                false, true, null)))),
                                Map.of());
        }

        private static List<NeTExEntityService.NeTExServiceJourney> serviceJourneys() {
                return List.of(
                                new NeTExEntityService.NeTExServiceJourney("FTR:ServiceJourney:1", "IC 1", "1",
                                                "FTR:JourneyPattern:IC-1-a", "FTR:Operator:vr", "FTR:Line:IC-1",
                                                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00",
                                                                "HKI", null,
                                                                                "FTR:StopPointInJourneyPattern:IC-1-a_1"),
                                                                new NeTExEntityService.NeTExPassingTime(2, "11:30:00",
                                                                                null, "OL", null,
                                                                                "FTR:StopPointInJourneyPattern:IC-1-a_2"))),
                                new NeTExEntityService.NeTExServiceJourney("FTR:ServiceJourney:2", "Z 2", "2",
                                                "FTR:JourneyPattern:Z-a", "FTR:Operator:vr", "FTR:Line:Z",
                                                List.of(new NeTExEntityService.NeTExPassingTime(1, null, "06:30:00",
                                                                "HKI", null,
                                                                "FTR:StopPointInJourneyPattern:Z-a_1"),
                                                                new NeTExEntityService.NeTExPassingTime(2, "12:30:00",
                                                                                null, "OL", null,
                                                                                "FTR:StopPointInJourneyPattern:Z-a_2"))));
        }
}
