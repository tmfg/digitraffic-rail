package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for FinnishRollingStock — wagon type to NeTEx enum mappings.
 * Source: https://fi.wikipedia.org/wiki/Sarjatunnus
 */
class FinnishRollingStockTest {

    // --- TrainElementType mapping ---

    @Test
    void givenRestaurantCarERd_whenMappingElementType_thenReturnsRestaurantCarriage() {
        // given
        final String wagonType = "ERd";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("restaurantCarriage", result);
    }

    @Test
    void givenRestaurantCarRx_whenMappingElementType_thenReturnsRestaurantCarriage() {
        // given
        final String wagonType = "Rx";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("restaurantCarriage", result);
    }

    @Test
    void givenSleepingCarEdm_whenMappingElementType_thenReturnsSleeperCarriage() {
        // given
        final String wagonType = "Edm";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("sleeperCarriage", result);
    }

    @Test
    void givenSleepingCarCEmt_whenMappingElementType_thenReturnsSleeperCarriage() {
        // given
        final String wagonType = "CEmt";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("sleeperCarriage", result);
    }

    @Test
    void givenCarTransporterGfot_whenMappingElementType_thenReturnsCarTransporter() {
        // given
        final String wagonType = "Gfot";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carTransporter", result);
    }

    @Test
    void givenCarTransporterGd_whenMappingElementType_thenReturnsCarTransporter() {
        // given
        final String wagonType = "Gd";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carTransporter", result);
    }

    @Test
    void givenLuggageVanFo_whenMappingElementType_thenReturnsLuggageVan() {
        // given
        final String wagonType = "Fo";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("luggageVan", result);
    }

    @Test
    void givenServiceCarDe_whenMappingElementType_thenReturnsOther() {
        // given
        final String wagonType = "De";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("other", result);
    }

    @Test
    void givenPowerCarNom_whenMappingElementType_thenReturnsOther() {
        // given
        final String wagonType = "Nom";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("other", result);
    }

    @Test
    void givenStandardCoachEd_whenMappingElementType_thenReturnsCarriage() {
        // given
        final String wagonType = "Ed";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carriage", result);
    }

    @Test
    void givenFirstClassCoachCEd_whenMappingElementType_thenReturnsCarriage() {
        // given
        final String wagonType = "CEd";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carriage", result);
    }

    @Test
    void givenEmuSm5_whenMappingElementType_thenReturnsCarriage() {
        // given
        final String wagonType = "Sm5";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carriage", result);
    }

    @Test
    void givenDmuDm12_whenMappingElementType_thenReturnsCarriage() {
        // given
        final String wagonType = "Dm12";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carriage", result);
    }

    @Test
    void givenNullWagonType_whenMappingElementType_thenReturnsCarriage() {
        // given / when
        final String result = FinnishRollingStock.mapWagonElementType(null);

        // then
        assertEquals("carriage", result);
    }

    @Test
    void givenUnknownWagonType_whenMappingElementType_thenReturnsCarriage() {
        // given
        final String wagonType = "XYZ";

        // when
        final String result = FinnishRollingStock.mapWagonElementType(wagonType);

        // then
        assertEquals("carriage", result);
    }

    // --- FareClass mapping ---

    @Test
    void givenFirstClassCEd_whenMappingFareClass_thenReturnsFirstClass() {
        // given
        final String wagonType = "CEd";

        // when
        final String result = FinnishRollingStock.mapFareClass(wagonType);

        // then
        assertEquals("firstClass", result);
    }

    @Test
    void givenFirstClassCEmt_whenMappingFareClass_thenReturnsFirstClass() {
        // given
        final String wagonType = "CEmt";

        // when
        final String result = FinnishRollingStock.mapFareClass(wagonType);

        // then
        assertEquals("firstClass", result);
    }

    @Test
    void givenStandardClassEd_whenMappingFareClass_thenReturnsStandardClass() {
        // given
        final String wagonType = "Ed";

        // when
        final String result = FinnishRollingStock.mapFareClass(wagonType);

        // then
        assertEquals("standardClass", result);
    }

    @Test
    void givenStandardClassEds_whenMappingFareClass_thenReturnsStandardClass() {
        // given
        final String wagonType = "Eds";

        // when
        final String result = FinnishRollingStock.mapFareClass(wagonType);

        // then
        assertEquals("standardClass", result);
    }

    @Test
    void givenNonPassengerGfot_whenMappingFareClass_thenReturnsNull() {
        // given
        final String wagonType = "Gfot";

        // when
        final String result = FinnishRollingStock.mapFareClass(wagonType);

        // then
        assertNull(result);
    }

    @Test
    void givenNullWagonType_whenMappingFareClass_thenReturnsNull() {
        // given / when
        final String result = FinnishRollingStock.mapFareClass(null);

        // then
        assertNull(result);
    }

    // --- Locomotive power type mapping ---

    @Test
    void givenElectricLocomotiveSr2_whenMappingPowerType_thenReturnsElectricity() {
        // given
        final String locomotiveType = "Sr2";

        // when
        final String result = FinnishRollingStock.mapLocoPowerType(locomotiveType);

        // then
        assertEquals("electricity", result);
    }

    @Test
    void givenElectricLocomotiveSr3_whenMappingPowerType_thenReturnsElectricity() {
        // given
        final String locomotiveType = "Sr3";

        // when
        final String result = FinnishRollingStock.mapLocoPowerType(locomotiveType);

        // then
        assertEquals("electricity", result);
    }

    @Test
    void givenDieselLocomotiveDr19_whenMappingPowerType_thenReturnsDiesel() {
        // given
        final String locomotiveType = "Dr19";

        // when
        final String result = FinnishRollingStock.mapLocoPowerType(locomotiveType);

        // then
        assertEquals("diesel", result);
    }

    @Test
    void givenUnknownLocomotiveType_whenMappingPowerType_thenReturnsOther() {
        // given
        final String locomotiveType = "Unknown";

        // when
        final String result = FinnishRollingStock.mapLocoPowerType(locomotiveType);

        // then
        assertEquals("other", result);
    }

    @Test
    void givenNullLocomotiveType_whenMappingPowerType_thenReturnsOther() {
        // given / when
        final String result = FinnishRollingStock.mapLocoPowerType(null);

        // then
        assertEquals("other", result);
    }
}
