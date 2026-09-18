package fi.livi.rata.avoindata.updater.service.gtfs;

import fi.livi.rata.avoindata.updater.config.InfraApiRetry;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.NearestPointsService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class TrakediaRouteServiceTest {
    private TrakediaRouteService service;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws Exception {
        service = new TrakediaRouteService();
        webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        setField("webClient", webClient);
        setField("nearestPointsService", mock(NearestPointsService.class));
        setField("wgs84ConversionService", mock(Wgs84ConversionService.class));
        // Same policy, negligible backoff, so retry counts are asserted without real sleeps.
        setField("retryTemplate", InfraApiRetry.create(1, 1));
    }

    @Test
    void givenServiceUnavailableWhenCreatingRouteThenTheRequestIsRetried() throws Exception {
        // Given
        final WebClientResponseException unavailable = WebClientResponseException.create(503, "unavailable", null, null, null);
        when(webClient.get().uri(anyString()).retrieve().bodyToMono(tools.jackson.databind.JsonNode.class).block())
                .thenThrow(unavailable);
        clearInvocations(webClient);

        // When / Then
        assertThatThrownBy(() -> service.createRoute(new Stop(null), new Stop(null), "start", "end"))
                .isSameAs(unavailable);

        verify(webClient, times(5)).get();
    }

    @Test
    void givenInternalServerErrorWhenCreatingRouteThenTheRequestIsNotRetried() throws Exception {
        // Given
        final WebClientResponseException internalError = WebClientResponseException.create(500, "internal error", null, null, null);
        when(webClient.get().uri(anyString()).retrieve().bodyToMono(tools.jackson.databind.JsonNode.class).block())
                .thenThrow(internalError);
        clearInvocations(webClient);

        // When / Then
        assertThatThrownBy(() -> service.createRoute(new Stop(null), new Stop(null), "start", "end"))
                .isSameAs(internalError);

        verify(webClient, times(1)).get();
    }

    private void setField(final String name, final Object value) throws Exception {
        final Field field = TrakediaRouteService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}