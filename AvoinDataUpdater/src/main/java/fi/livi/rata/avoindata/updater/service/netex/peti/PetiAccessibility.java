package fi.livi.rata.avoindata.updater.service.netex.peti;

/**
 * Accessibility information parsed from a PETI NeTEx AccessibilityAssessment.
 * All fields use tri-state (TRUE/FALSE/UNKNOWN).
 */
public record PetiAccessibility(
        PetiLimitationStatus wheelchairAccess,
        PetiLimitationStatus stepFreeAccess,
        PetiLimitationStatus liftFreeAccess,
        PetiLimitationStatus escalatorFreeAccess,
        PetiLimitationStatus audibleSignalsAvailable,
        PetiLimitationStatus visualSignsAvailable
) {
}
