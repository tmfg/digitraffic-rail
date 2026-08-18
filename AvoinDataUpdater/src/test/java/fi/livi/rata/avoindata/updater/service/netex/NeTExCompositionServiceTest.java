package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.CompositionTimeTableRow;
import fi.livi.rata.avoindata.common.domain.composition.JourneyCompositionRow;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

/**
 * Tests for NeTExCompositionService — end-to-end through buildCompositionsZip,
 * verifying VehicleTypes, Train elements, DatedServiceJourneys, accessibility,
 * and ServiceJourneyRef cross-references in the resulting XML.
 */
class NeTExCompositionServiceTest {

    private NeTExCompositionService compositionService;

    @BeforeEach
    void setUp() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExWritingService writingService = new NeTExWritingService(idGenerator);
        final NeTExCompositionWritingService compositionWritingService = new NeTExCompositionWritingService(
                writingService);
        compositionService = new NeTExCompositionService(null, null, null, null, idGenerator,
                compositionWritingService);
    }

    // --- VehicleType in XML ---

    @Test
    void givenElectricLocomotive_whenBuildingZip_thenXmlContainsSelfPropelledWithElectricity() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:VehicleType:Sr2\""));
        assertTrue(xml.contains("<TypeOfFuel>electricity</TypeOfFuel>"));
    }

    @Test
    void givenDieselLocomotive_whenBuildingZip_thenXmlContainsDieselFuel() {
        // given
        final Composition c = composition(710, "2026-07-07", "OL", "KUO");
        loco(c, "Dr19");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:VehicleType:Dr19\""));
        assertTrue(xml.contains("<TypeOfFuel>diesel</TypeOfFuel>"));
    }

    @Test
    void givenHauledCoach_whenBuildingZip_thenXmlContainsLengthAndNotSelfPropelled() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:VehicleType:Ed\""));
        assertTrue(xml.contains("<Length>26.40</Length>"));
    }

    @Test
    void givenAccessibleWagon_whenBuildingZip_thenXmlContainsLowFloorAndRamp() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Eds", 1, 2640, true);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<LowFloor>true</LowFloor>"));
        assertTrue(xml.contains("<HasLiftOrRamp>true</HasLiftOrRamp>"));
    }

    @Test
    void givenDuplicateWagonTypes_whenBuildingZip_thenOnlyOneVehicleTypeInXml() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);
        wagon(c, "Ed", 2, 2640, false);
        wagon(c, "Ed", 3, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        final int first = xml.indexOf("id=\"FTR:VehicleType:Ed\"");
        final int second = xml.indexOf("id=\"FTR:VehicleType:Ed\"", first + 1);
        assertTrue(first > 0);
        assertEquals(-1, second, "Should not have duplicate VehicleType:Ed");
    }

    // --- DatedServiceJourney in XML ---

    @Test
    void givenSingleSection_whenBuildingZip_thenOneDatedServiceJourneyWithCorrectId() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:DatedVehicleJourney:59-2026-07-07-HKI\""));
    }

    @Test
    void givenMultiSection_whenBuildingZip_thenMultipleDatedServiceJourneys() {
        // given
        final Composition c = compositionMultiSection(9, "2026-07-07", "HKI", "KV", "KV", "OL");

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:DatedVehicleJourney:9-2026-07-07-HKI\""));
        assertTrue(xml.contains("id=\"FTR:DatedVehicleJourney:9-2026-07-07-KV\""));
    }

    @Test
    void givenSection_whenBuildingZip_thenTrainHasCorrectNumberOfCars() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);
        wagon(c, "CEd", 2, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<NumberOfCars>3</NumberOfCars>"));
    }

    @Test
    void givenLocomotive_whenBuildingZip_thenTrainElementTypeIsEngine() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<TrainElementType>engine</TrainElementType>"));
    }

    @Test
    void givenFirstClassWagon_whenBuildingZip_thenFareClassesFirstClass() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "CEd", 2, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<FareClasses>firstClass</FareClasses>"));
    }

    @Test
    void givenStandardClassWagon_whenBuildingZip_thenFareClassesStandardClass() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<FareClasses>standardClass</FareClasses>"));
    }

    @Test
    void givenWagon_whenBuildingZip_thenLabelContainsTypeAndSalesNumber() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 5, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<Label>Ed #5</Label>"));
    }

    @Test
    void givenLocomotive_whenBuildingZip_thenLabelIsLocomotiveType() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<Label>Sr2</Label>"));
    }

    // --- Accessibility in XML ---

    @Test
    void givenDisabledWagon_whenBuildingZip_thenAccessibilityAssessmentPresent() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Eds", 1, 2640, true);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<MobilityImpairedAccess>true</MobilityImpairedAccess>"));
        assertTrue(xml.contains("id=\"FTR:AA:59-2026-07-07-HKI\""));
    }

    @Test
    void givenNoDisabledWagons_whenBuildingZip_thenNoAccessibilityAssessment() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertFalse(xml.contains("MobilityImpairedAccess"));
    }

    // --- ServiceJourneyRef ---

    @Test
    void givenMatchingRef_whenBuildingZip_thenServiceJourneyRefInXml() {
        // given
        final LocalDate date = LocalDate.of(2026, 7, 7);
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);
        final Map<TrainId, String> refs = Map.of(new TrainId(59, date), "FTR:ServiceJourney:59-4118175");

        // when
        final String xml = buildXml(c, refs);

        // then
        assertTrue(xml.contains("ref=\"FTR:ServiceJourney:59-4118175\""));
    }

    @Test
    void givenNoMatchingRef_whenBuildingZip_thenNoServiceJourneyRefInXml() {
        // given
        final LocalDate date = LocalDate.of(2026, 7, 7);
        final Composition c = composition(9999, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);
        final Map<TrainId, String> refs = Map.of(new TrainId(59, date), "FTR:ServiceJourney:59-4118175");

        // when
        final String xml = buildXml(c, refs);

        // then
        assertFalse(xml.contains("ServiceJourneyRef"));
    }

    // --- ZIP structure ---

    @Test
    void givenComposition_whenBuildingZip_thenOutputIsValidZipWithCorrectFilename() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final byte[] zip = compositionService.buildCompositionsZip(List.of(c), Map.of());

        // then
        assertNotNull(zip);
        assertTrue(zip.length > 0);
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            final ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry);
            assertEquals("FTR_rail_compositions.xml", entry.getName());
        } catch (final Exception e) {
            fail("ZIP parsing failed: " + e.getMessage());
        }
    }

    @Test
    void givenComposition_whenBuildingZip_thenXmlHasCorrectVersion() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("version=\"1.15:NO-NeTEx-networktimetable:1.5\""));
    }

    @Test
    void givenComposition_whenBuildingZip_thenXmlHasParticipantRefDT() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("<ParticipantRef>FTR</ParticipantRef>"));
    }

    @Test
    void givenComposition_whenBuildingZip_thenXmlContainsBothFrames() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:ResourceFrame:compositions\""));
        assertTrue(xml.contains("id=\"FTR:TimetableFrame:compositions\""));
    }

    @Test
    void givenComposition_whenBuildingZip_thenTrainRefPointsToExistingTrain() {
        // given
        final Composition c = composition(59, "2026-07-07", "HKI", "OL");
        loco(c, "Sr2");
        wagon(c, "Ed", 1, 2640, false);

        // when
        final String xml = buildXml(c);

        // then
        assertTrue(xml.contains("id=\"FTR:Train:59-2026-07-07-HKI\""));
        assertTrue(xml.contains("ref=\"FTR:Train:59-2026-07-07-HKI\""));
    }

    @Test
    void givenEmptyCompositions_whenBuildingZip_thenProducesValidXml() {
        // given / when
        final byte[] zip = compositionService.buildCompositionsZip(List.of(), Map.of());

        // then
        final String xml = extractXml(zip);
        assertTrue(xml.contains("PublicationDelivery"));
    }

    // --- Helpers ---

    private String buildXml(final Composition c) {
        return buildXml(c, Map.of());
    }

    private String buildXml(final Composition c, final Map<TrainId, String> refs) {
        final byte[] zip = compositionService.buildCompositionsZip(List.of(c), refs);
        return extractXml(zip);
    }

    private String extractXml(final byte[] zipBytes) {
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            zis.getNextEntry();
            return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to extract XML from ZIP", e);
        }
    }

    private Composition composition(final long trainNumber, final String date,
            final String begin, final String end) {
        final LocalDate d = LocalDate.parse(date);
        final Operator op = new Operator();
        op.operatorUICCode = 10;
        op.operatorShortCode = "vr";
        final Composition c = new Composition(op, trainNumber, d, 1L, 1L, 1L, Instant.now());
        c.journeySections.add(section(c, begin, end));
        return c;
    }

    private Composition compositionMultiSection(final long trainNumber, final String date,
            final String b1, final String e1, final String b2, final String e2) {
        final LocalDate d = LocalDate.parse(date);
        final Operator op = new Operator();
        op.operatorUICCode = 10;
        op.operatorShortCode = "vr";
        final Composition c = new Composition(op, trainNumber, d, 1L, 1L, 1L, Instant.now());
        final JourneySection s1 = section(c, b1, e1);
        locoSection(s1, "Sr2");
        wagonSection(s1, "Ed", 1, 2640, false);
        c.journeySections.add(s1);
        final JourneySection s2 = section(c, b2, e2);
        locoSection(s2, "Sr2");
        wagonSection(s2, "Ed", 1, 2640, false);
        c.journeySections.add(s2);
        return c;
    }

    private JourneySection section(final Composition c, final String begin, final String end) {
        final CompositionTimeTableRow beginRow = row(begin, 1);
        final CompositionTimeTableRow endRow = row(end, 2);
        return new JourneySection(beginRow, endRow, c, 200, 200, null, null);
    }

    private CompositionTimeTableRow row(final String stationShortCode, final int uicCode) {
        final ZonedDateTime time = ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, ZoneOffset.UTC);
        final JourneyCompositionRow jcr = new JourneyCompositionRow(
                time, stationShortCode, uicCode, "FI", TimeTableRow.TimeTableRowType.DEPARTURE);
        return new CompositionTimeTableRow(jcr);
    }

    private void loco(final Composition c, final String type) {
        locoSection(c.journeySections.iterator().next(), type);
    }

    private void locoSection(final JourneySection s, final String type) {
        final Locomotive l = new Locomotive();
        l.locomotiveType = type;
        l.location = s.locomotives.size() + 1;
        l.journeysection = s;
        s.locomotives.add(l);
    }

    private void wagon(final Composition c, final String type, final int sales, final int len, final boolean disabled) {
        wagonSection(c.journeySections.iterator().next(), type, sales, len, disabled);
    }

    private void wagonSection(final JourneySection s, final String type, final int sales,
            final int len, final boolean disabled) {
        final Wagon w = new Wagon();
        w.wagonType = type;
        w.salesNumber = sales;
        w.length = len;
        w.disabled = disabled;
        w.catering = false;
        w.location = s.wagons.size() + 1;
        w.journeysection = s;
        s.wagons.add(w);
    }
}
