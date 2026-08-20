package fi.livi.rata.avoindata.updater.service.gtfs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for Infra-API platform parsing. Infra-API omits the "liikennepaikanOsa" key
 * entirely when a platform has no value for it; the parser must fall back to "rautatieliikennepaikka"
 * instead of throwing a NullPointerException (which previously wiped out every platform's geometry).
 */
public class InfraApiPlatformServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String GEOMETRIA = "[[[506423.228795,6943376.039063],[506422.0625,6943401.15625]]]";

    private InfraApiPlatformService service;

    @BeforeEach
    public void setUp() throws Exception {
        final Wgs84ConversionService wgs84 = new Wgs84ConversionService();
        final Method setup = Wgs84ConversionService.class.getDeclaredMethod("setup");
        setup.setAccessible(true);
        setup.invoke(wgs84);

        service = new InfraApiPlatformService();
        final Field field = InfraApiPlatformService.class.getDeclaredField("wgs84ConversionService");
        field.setAccessible(true);
        field.set(service, wgs84);
    }

    @Test
    public void deserializePlatform_withoutLiikennepaikanOsaKey_fallsBackToRautatieliikennepaikka() {
        final JsonNode node = MAPPER.readTree("""
                {
                  "tunnus": "Laituri HKI L7",
                  "kuvaus": "Helsinki laituri: 7",
                  "kaupallinenNumero": "7",
                  "rautatieliikennepaikka": "1.2.246.586.1.39.119030",
                  "geometria": %s
                }
                """.formatted(GEOMETRIA));

        final InfraApiPlatform platform = service.deserializePlatform(node);

        assertEquals("1.2.246.586.1.39.119030", platform.liikennepaikkaId);
        assertEquals("7", platform.commercialTrack);
        assertFalse(platform.geometry.isEmpty(), "geometry should be parsed");
    }

    @Test
    public void deserializePlatform_withLiikennepaikanOsa_usesIt() {
        final JsonNode node = MAPPER.readTree("""
                {
                  "tunnus": "Laituri SNJ L1",
                  "kuvaus": "Suonenjoki laituri: 1",
                  "kaupallinenNumero": "1",
                  "liikennepaikanOsa": "1.2.246.586.1.37.2580000",
                  "rautatieliikennepaikka": "1.2.246.586.1.39.999999",
                  "geometria": %s
                }
                """.formatted(GEOMETRIA));

        final InfraApiPlatform platform = service.deserializePlatform(node);

        assertEquals("1.2.246.586.1.37.2580000", platform.liikennepaikkaId);
    }

    @Test
    public void deserializePlatform_withoutAnyLiikennepaikka_yieldsEmptyId() {
        final JsonNode node = MAPPER.readTree("""
                {
                  "tunnus": "Laituri X L1",
                  "kuvaus": "X laituri: 1",
                  "kaupallinenNumero": "1",
                  "geometria": %s
                }
                """.formatted(GEOMETRIA));

        final InfraApiPlatform platform = service.deserializePlatform(node);

        assertEquals("", platform.liikennepaikkaId);
    }
}
