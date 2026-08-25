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

/**
 * Dataset-level invariants of the per-Line split: file scoping rules of the
 * Nordic profile, and id uniqueness across the files of the dataset.
 */
class NeTExDatasetLayoutTest {

    private static final Pattern NETEX_ID = Pattern.compile("\\bid=\"(FTR:[^\"]+)\"");

    private NeTExWritingService writingService;

    @BeforeEach
    void setUp() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        writingService = new NeTExWritingService(idGenerator, new NeTExCompositionWritingService(idGenerator));
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
            for (final String id : declaredIds(entry.getValue())) {
                final String previous = owningFile.putIfAbsent(id, entry.getKey());
                if (previous != null && !previous.equals(entry.getKey())) {
                    duplicates.add(id + " in " + previous + " and " + entry.getKey());
                }
            }
        }

        assertTrue(duplicates.isEmpty(), "Duplicated NeTEx ids across files: " + duplicates);
    }

    @Test
    void givenCommonFile_whenBuildingDataset_thenItCarriesNoLineRouteJourneyPatternOrTimetableFrame()
            throws Exception {
        final String shared = build().get("_FTR_shared_data.xml");

        assertFalse(shared.contains("<TimetableFrame"), "TimetableFrame is illegal in a common file");
        assertFalse(shared.contains("<Line "), "Line is illegal in a common file ServiceFrame");
        assertFalse(shared.contains("<Route "), "Route is illegal in a common file ServiceFrame");
        assertFalse(shared.contains("<JourneyPattern "), "JourneyPattern is illegal in a common file ServiceFrame");
    }

    @Test
    void givenLineFile_whenBuildingDataset_thenItHasExactlyOneResourceFrameAndItsOwnLine() throws Exception {
        final String lineFile = build().get("FTR_IC-1_Helsinki-Oulu.xml");

        assertEquals(1, countOccurrences(lineFile, "<ResourceFrame"));
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

        // IC-1 runs on 2026-08-20 only, Z on 2026-08-22 only.
        assertTrue(files.get("FTR_IC-1_Helsinki-Oulu.xml").contains("<FromDate>2026-08-20T00:00:00</FromDate>"));
        assertTrue(files.get("FTR_IC-1_Helsinki-Oulu.xml").contains("<ToDate>2026-08-21T04:00:00</ToDate>"));
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
    void givenCompositions_whenBuildingDataset_thenVehicleTypesAreSharedAndTrainsPerLine() {
        // given: a composition for the (train, day) of the IC-1 dated journey
        final var composition = new NeTExCompositionService.NeTExDatedVehicleJourney(
                "FTR:DatedServiceJourney:1-2026-08-20", 1L, LocalDate.of(2026, 8, 20), "HKI", "OL",
                200, 200, true, false, "FTR:ServiceJourney:1",
                List.of(new NeTExCompositionService.NeTExTrainComponent(1, "engine",
                        "FTR:VehicleType:Sr2", "Sr2", 2000, false, false, null)));
        final var compositions = new NeTExCompositionService.CompositionData(
                List.of(new NeTExCompositionService.NeTExVehicleType("FTR:VehicleType:Sr2", "Sr2",
                        "electricity", true, 2000, false, false)),
                List.of(composition));

        final var dated = List.of(
                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:1-2026-08-20",
                        "FTR:ServiceJourney:1", LocalDate.of(2026, 8, 20)),
                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:2-2026-08-22",
                        "FTR:ServiceJourney:2", LocalDate.of(2026, 8, 22)));

        // when
        final Map<String, String> files = marshal(writingService.buildDataset(
                stopsData(), routeData(), lines(), operators(),
                serviceJourneys(), dated, compositions,
                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC)));

        // then: rolling stock types are cross-line, formations belong to their Line
        final String shared = files.get("_FTR_shared_data.xml");
        final String ic1 = files.get("FTR_IC-1_Helsinki-Oulu.xml");
        final String z = files.get("FTR_Z_Helsinki-Lahti.xml");

        assertTrue(shared.contains("id=\"FTR:VehicleType:Sr2\""), "VehicleType belongs to the common file");
        assertFalse(ic1.contains("id=\"FTR:VehicleType:Sr2\""), "VehicleType must not repeat in a line file");

        assertTrue(ic1.contains("id=\"FTR:Train:1-2026-08-20-HKI\""), "Train belongs to its line file");
        assertTrue(ic1.contains("ref=\"FTR:Train:1-2026-08-20-HKI\""), "journey must reference its formation");
        assertFalse(z.contains("FTR:Train:1-2026-08-20-HKI"), "another line must not carry the formation");

        assertTrue(ic1.contains("<AccessibilityAssessment"), "wheelchair access is folded onto the journey");
    }

    // --- Helpers ---

    private Map<String, String> marshal(final Map<String, PublicationDeliveryStructure> dataset) {
        final byte[] zip = writingService.marshalAndZip(dataset);
        final Map<String, String> files = new LinkedHashMap<>();
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to read dataset", e);
        }
        return files;
    }

    private Map<String, String> build() throws Exception {
        final byte[] zip = writingService.writeNeTExZip(
                stopsData(), routeData(), lines(), operators(),
                serviceJourneys(), datedServiceJourneys(),
                ZonedDateTime.of(2026, 8, 21, 6, 12, 0, 0, ZoneOffset.UTC));

        if (System.getProperty("netex.dump") != null) {
            NeTExSampleDump.dump(zip, System.getProperty("netex.dump"));
        }

        final Map<String, String> files = new LinkedHashMap<>();
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static Set<String> declaredIds(final String xml) {
        final Set<String> ids = new HashSet<>();
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
                List.of(new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:HKI", "Helsinki", "HKI",
                        new BigDecimal("60.172133"), new BigDecimal("24.941662")),
                        new NeTExStopsData.NeTExScheduledStopPoint("FTR:ScheduledStopPoint:OL", "Oulu", "OL",
                                new BigDecimal("65.011153"), new BigDecimal("25.470834"))),
                List.of(new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:HKI", "HKI"),
                        new NeTExStopsData.NeTExRoutePoint("FTR:RoutePoint:OL", "OL")),
                List.of(new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:HKI", "Helsinki"),
                        new NeTExStopsData.NeTExDestinationDisplay("FTR:DestinationDisplay:OL", "Oulu")));
    }

    private static NeTExRouteData routeData() {
        return new NeTExRouteData(
                List.of(new NeTExRouteData.NeTExRoute("FTR:Route:IC-1-a", "HKI - OL", "FTR:Line:IC-1",
                        List.of("FTR:RoutePoint:HKI", "FTR:RoutePoint:OL")),
                        new NeTExRouteData.NeTExRoute("FTR:Route:Z-a", "HKI - OL", "FTR:Line:Z",
                                List.of("FTR:RoutePoint:HKI", "FTR:RoutePoint:OL"))),
                List.of(new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:IC-1-a", "FTR:Route:IC-1-a",
                        List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "FTR:ScheduledStopPoint:HKI",
                                true, false, "FTR:DestinationDisplay:OL"),
                                new NeTExRouteData.NeTExStopPointInPattern(2, "FTR:ScheduledStopPoint:OL",
                                        false, true, null))),
                        new NeTExRouteData.NeTExJourneyPattern("FTR:JourneyPattern:Z-a", "FTR:Route:Z-a",
                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1, "FTR:ScheduledStopPoint:HKI",
                                        true, false, "FTR:DestinationDisplay:OL"),
                                        new NeTExRouteData.NeTExStopPointInPattern(2, "FTR:ScheduledStopPoint:OL",
                                                false, true, null)))),
                Map.of());
    }

    private static List<NeTExEntityService.NeTExServiceJourney> serviceJourneys() {
        return List.of(
                new NeTExEntityService.NeTExServiceJourney("FTR:ServiceJourney:1", "IC 1", "1",
                        "FTR:JourneyPattern:IC-1-a", "FTR:Operator:vr", "FTR:Line:IC-1",
                        List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00", "HKI", null,
                                "FTR:StopPointInJourneyPattern:IC-1-a-1"),
                                new NeTExEntityService.NeTExPassingTime(2, "11:30:00", null, "OL", null,
                                        "FTR:StopPointInJourneyPattern:IC-1-a-2"))),
                new NeTExEntityService.NeTExServiceJourney("FTR:ServiceJourney:2", "Z 2", "2",
                        "FTR:JourneyPattern:Z-a", "FTR:Operator:vr", "FTR:Line:Z",
                        List.of(new NeTExEntityService.NeTExPassingTime(1, null, "06:30:00", "HKI", null,
                                "FTR:StopPointInJourneyPattern:Z-a-1"),
                                new NeTExEntityService.NeTExPassingTime(2, "12:30:00", null, "OL", null,
                                        "FTR:StopPointInJourneyPattern:Z-a-2"))));
    }

    private static List<NeTExEntityService.NeTExDatedServiceJourney> datedServiceJourneys() {
        return List.of(
                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:1",
                        "FTR:ServiceJourney:1", LocalDate.of(2026, 8, 20)),
                new NeTExEntityService.NeTExDatedServiceJourney("FTR:DatedServiceJourney:2",
                        "FTR:ServiceJourney:2", LocalDate.of(2026, 8, 22)));
    }
}
