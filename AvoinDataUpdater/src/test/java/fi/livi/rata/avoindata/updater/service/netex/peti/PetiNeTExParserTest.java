package fi.livi.rata.avoindata.updater.service.netex.peti;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PetiNeTExParser — XML to PetiStop model parsing.
 */
class PetiNeTExParserTest {

    private PetiNeTExParser parser;

    @BeforeEach
    void setUp() {
        parser = new PetiNeTExParser();
    }

    private InputStream fixtureStream() {
        return getClass().getClassLoader().getResourceAsStream("peti/stops-fixture.xml");
    }

    // --- A1: Parse fixture yields expected StopPlace count ---

    @Test
    void givenFixtureWith4ValidAnd2MalformedStopPlaces_whenParse_thenResultHas4Entries() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — 4 valid (Tervola parent, Tervola child, Testilä, Sekalainen); 2 skipped (INVALID, NOUIC)
        assertEquals(4, result.size());
    }

    // --- A2: StopPlace ID is verbatim ---

    @Test
    void givenTervolaInFixture_whenParsed_thenStopPlaceIdIsVerbatim() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiStop tervola = result.stream()
                .filter(s -> s.stopPlaceId().equals("FSR:StopPlace:1"))
                .findFirst()
                .orElseThrow();
        assertEquals("FSR:StopPlace:1", tervola.stopPlaceId());
    }

    // --- A3: UIC code read from keyList as integer ---

    @Test
    void givenTervolaUicCode1000361_whenParsed_thenUicCodeIs1000361() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiStop tervola = findByStopPlaceId(result, "FSR:StopPlace:1");
        assertEquals(1000361, tervola.uicCode());
    }

    // --- A4: IS_PARENT_STOP_PLACE flag parsed as boolean ---

    @Test
    void givenTervolaWithIsParentTrue_whenParsed_thenParentStopPlaceIsTrue() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiStop tervola = findByStopPlaceId(result, "FSR:StopPlace:1");
        assertTrue(tervola.parentStopPlace());
    }

    // --- A5: IS_PARENT_STOP_PLACE absent defaults to false ---

    @Test
    void givenChildStopPlaceWithoutIsParentKey_whenParsed_thenParentStopPlaceIsFalse() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:2 has no IS_PARENT_STOP_PLACE key
        final PetiStop child = findByStopPlaceId(result, "FSR:StopPlace:2");
        assertFalse(child.parentStopPlace());
    }

    // --- A6: Quays parsed with correct IDs and publicCode ---

    @Test
    void givenTervolaWithTwoQuays_whenParsed_thenQuaysHaveCorrectIdsAndPublicCodes() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiStop tervola = findByStopPlaceId(result, "FSR:StopPlace:1");
        assertEquals(2, tervola.quays().size());

        final PetiQuay quay7 = tervola.quays().stream()
                .filter(q -> q.quayId().equals("FSR:Quay:7"))
                .findFirst()
                .orElseThrow();
        assertEquals("1", quay7.publicCode());

        final PetiQuay quay10 = tervola.quays().stream()
                .filter(q -> q.quayId().equals("FSR:Quay:10"))
                .findFirst()
                .orElseThrow();
        assertEquals("2", quay10.publicCode());
    }

    // --- A6b: Quay centroid parsed, and absent centroid leaves the quay unlocated ---

    @Test
    void givenQuayWithCentroid_whenParsed_thenCoordinatesAreRead() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiQuay quay7 = findByStopPlaceId(result, "FSR:StopPlace:1").quays().stream()
                .filter(q -> q.quayId().equals("FSR:Quay:7"))
                .findFirst()
                .orElseThrow();
        assertTrue(quay7.hasLocation());
        assertEquals(new BigDecimal("66.081168"), quay7.latitude());
        assertEquals(new BigDecimal("24.771454"), quay7.longitude());
    }

    @Test
    void givenQuayWithoutCentroid_whenParsed_thenQuayHasNoLocation() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then
        final PetiQuay quay10 = findByStopPlaceId(result, "FSR:StopPlace:1").quays().stream()
                .filter(q -> q.quayId().equals("FSR:Quay:10"))
                .findFirst()
                .orElseThrow();
        assertFalse(quay10.hasLocation());
        assertNull(quay10.latitude());
        assertNull(quay10.longitude());
    }

    // --- A7: StopPlace with zero quays yields empty quay list ---

    @Test
    void givenStopPlaceWithNoQuays_whenParsed_thenQuayListIsEmpty() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:99 has no quays element
        final PetiStop noQuays = findByStopPlaceId(result, "FSR:StopPlace:99");
        assertNotNull(noQuays.quays());
        assertTrue(noQuays.quays().isEmpty());
    }

    // --- A8: Transient peti_numeric_id KeyValue is ignored ---

    @Test
    void givenFixtureWithPetiNumericId_whenParsed_thenNoFieldReflectsValue42() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — PetiStop only has: stopPlaceId, uicCode, name, parentStopPlace, accessibility, quays
        // The value 42 from peti_numeric_id must not appear in any of these
        final PetiStop tervola = findByStopPlaceId(result, "FSR:StopPlace:1");
        assertNotEquals(42, tervola.uicCode());
        assertEquals("Tervola", tervola.name());
        // Model has no other numeric field that could contain 42
    }

    // --- A9: StopPlace with malformed (non-numeric) uicCode is skipped ---

    @Test
    void givenStopPlaceWithNonNumericUicCode_whenParsed_thenSkippedWithNoException() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:BAD with "INVALID" uicCode is not in result
        assertTrue(result.stream().noneMatch(s -> s.stopPlaceId().equals("FSR:StopPlace:BAD")));
    }

    // --- A10: StopPlace with missing uicCode KeyValue is skipped ---

    @Test
    void givenStopPlaceWithNoUicCodeKey_whenParsed_thenSkippedWithNoException() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:NOUIC has no uicCode key
        assertTrue(result.stream().noneMatch(s -> s.stopPlaceId().equals("FSR:StopPlace:NOUIC")));
    }

    // --- A10b: A structurally malformed document fails fast (does not return partial/empty) ---

    @Test
    void givenMalformedXmlDocument_whenParsed_thenThrowsPetiParseException() {
        // given — truncated / non-well-formed XML (unclosed element)
        final InputStream xml = new ByteArrayInputStream(
                "<PublicationDelivery><dataObjects><StopPlace id=\"FSR:StopPlace:1\">"
                        .getBytes(StandardCharsets.UTF_8));

        // when / then — the whole call fails rather than silently yielding a partial result
        assertThrows(PetiParseException.class, () -> parser.parse(xml));
    }

    // --- A11: Accessibility parsed at StopPlace level ---

    @Test
    void givenStopPlaceWithAccessibility_whenParsed_thenAccessibilityFieldsPopulated() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:50 has wheelchairAccess=true, stepFreeAccess=false
        final PetiStop mixed = findByStopPlaceId(result, "FSR:StopPlace:50");
        assertNotNull(mixed.accessibility());
        assertEquals(PetiLimitationStatus.TRUE, mixed.accessibility().wheelchairAccess());
        assertEquals(PetiLimitationStatus.FALSE, mixed.accessibility().stepFreeAccess());
    }

    // --- A12: Accessibility parsed at Quay level ---

    @Test
    void givenQuayWithOwnAccessibility_whenParsed_thenQuayAccessibilityIsIndependent() {
        // given
        final InputStream xml = fixtureStream();

        // when
        final List<PetiStop> result = parser.parse(xml);

        // then — FSR:StopPlace:50 / FSR:Quay:51 has liftFreeAccess=unknown (quay-level)
        final PetiStop mixed = findByStopPlaceId(result, "FSR:StopPlace:50");
        final PetiQuay quay51 = mixed.quays().stream()
                .filter(q -> q.quayId().equals("FSR:Quay:51"))
                .findFirst()
                .orElseThrow();
        assertNotNull(quay51.accessibility());
        assertEquals(PetiLimitationStatus.UNKNOWN, quay51.accessibility().liftFreeAccess());
        assertEquals(PetiLimitationStatus.TRUE, quay51.accessibility().wheelchairAccess());
        assertEquals(PetiLimitationStatus.FALSE, quay51.accessibility().audibleSignalsAvailable());
    }

    // --- Helper ---

    private PetiStop findByStopPlaceId(final List<PetiStop> stops, final String id) {
        return stops.stream()
                .filter(s -> s.stopPlaceId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("StopPlace not found: " + id));
    }
}
