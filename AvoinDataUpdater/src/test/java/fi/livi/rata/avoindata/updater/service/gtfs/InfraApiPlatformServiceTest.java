package fi.livi.rata.avoindata.updater.service.gtfs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fi.livi.rata.avoindata.updater.config.InfraApiRetry;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for Infra-API platform parsing. Infra-API omits the "liikennepaikanOsa" key
 * entirely when a platform has no value for it; the parser must fall back to "rautatieliikennepaikka"
 * instead of throwing a NullPointerException (which previously wiped out every platform's geometry).
 */
public class InfraApiPlatformServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String GEOMETRIA = "[[[506423.228795,6943376.039063],[506422.0625,6943401.15625]]]";

    private InfraApiPlatformService service;
    private WebClient webClient;

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
        // Same policy, negligible backoff, so retry counts are asserted without real sleeps.
        setField("retryTemplate", InfraApiRetry.create(1, 1));
    }

      @Test
      void givenServiceUnavailableWhenFetchingPlatformsThenTheRequestIsRetriedAndFailureIsNotPartialSuccess() {
        // Given
        webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        final WebClientResponseException unavailable = WebClientResponseException.create(503, "unavailable", null,
            null, null);
        when(webClient.get().uri("platforms").retrieve().bodyToMono(JsonNode.class).block())
            .thenThrow(unavailable);
        setField("webClient", webClient);
        setField("baseUrl", "platforms");
        clearInvocations(webClient);

        // When / Then
        assertThatThrownBy(() -> service.getPlatformsByLiikennepaikkaIdPart(null, null))
            .isSameAs(unavailable);
        verify(webClient, times(5)).get();
      }

      private void setField(final String name, final Object value) {
        try {
          final Field field = InfraApiPlatformService.class.getDeclaredField(name);
          field.setAccessible(true);
          field.set(service, value);
        } catch (final ReflectiveOperationException e) {
          throw new AssertionError(e);
        }
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
