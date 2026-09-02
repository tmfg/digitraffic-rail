package fi.livi.rata.avoindata.updater.service.netex.peti;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PetiAccessibility record construction and tri-state handling.
 */
class PetiAccessibilityTest {

    // --- D1: All accessibility fields TRUE ---

    @Test
    void givenAllLimitationsTrue_whenConstructed_thenAllFieldsAreTrue() {
        // given / when
        final PetiAccessibility accessibility = new PetiAccessibility(
                PetiLimitationStatus.TRUE,
                PetiLimitationStatus.TRUE,
                PetiLimitationStatus.TRUE,
                PetiLimitationStatus.TRUE,
                PetiLimitationStatus.TRUE,
                PetiLimitationStatus.TRUE
        );

        // then
        assertEquals(PetiLimitationStatus.TRUE, accessibility.wheelchairAccess());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.stepFreeAccess());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.liftFreeAccess());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.escalatorFreeAccess());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.audibleSignalsAvailable());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.visualSignsAvailable());
    }

    // --- D2: All accessibility fields FALSE ---

    @Test
    void givenAllLimitationsFalse_whenConstructed_thenAllFieldsAreFalse() {
        // given / when
        final PetiAccessibility accessibility = new PetiAccessibility(
                PetiLimitationStatus.FALSE,
                PetiLimitationStatus.FALSE,
                PetiLimitationStatus.FALSE,
                PetiLimitationStatus.FALSE,
                PetiLimitationStatus.FALSE,
                PetiLimitationStatus.FALSE
        );

        // then
        assertEquals(PetiLimitationStatus.FALSE, accessibility.wheelchairAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.stepFreeAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.liftFreeAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.escalatorFreeAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.audibleSignalsAvailable());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.visualSignsAvailable());
    }

    // --- D3: Mixed accessibility including UNKNOWN tri-state ---

    @Test
    void givenMixedLimitations_whenConstructed_thenFieldsReflectMixedState() {
        // given / when
        final PetiAccessibility accessibility = new PetiAccessibility(
                PetiLimitationStatus.TRUE,     // wheelchairAccess
                PetiLimitationStatus.FALSE,    // stepFreeAccess
                PetiLimitationStatus.UNKNOWN,  // liftFreeAccess
                PetiLimitationStatus.TRUE,     // escalatorFreeAccess
                PetiLimitationStatus.FALSE,    // audibleSignalsAvailable
                PetiLimitationStatus.UNKNOWN   // visualSignsAvailable
        );

        // then
        assertEquals(PetiLimitationStatus.TRUE, accessibility.wheelchairAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.stepFreeAccess());
        assertEquals(PetiLimitationStatus.UNKNOWN, accessibility.liftFreeAccess());
        assertEquals(PetiLimitationStatus.TRUE, accessibility.escalatorFreeAccess());
        assertEquals(PetiLimitationStatus.FALSE, accessibility.audibleSignalsAvailable());
        assertEquals(PetiLimitationStatus.UNKNOWN, accessibility.visualSignsAvailable());
    }

    // --- D4: Null/absent AccessibilityAssessment yields null accessibility ---

    @Test
    void givenStopOrQuayWithNoAccessibility_whenChecked_thenAccessibilityIsNull() {
        // given — PetiStop with null accessibility
        final PetiStop stop = new PetiStop("FSR:StopPlace:99", 1000500, "Testilä", true, null,
                java.util.List.of());

        // when / then
        assertNull(stop.accessibility());

        // given — PetiQuay with null accessibility
        final PetiQuay quay = new PetiQuay("FSR:Quay:20", "1", null, null, null);

        // when / then
        assertNull(quay.accessibility());
    }
}
