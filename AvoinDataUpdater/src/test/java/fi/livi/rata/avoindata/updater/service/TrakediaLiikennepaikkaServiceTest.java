package fi.livi.rata.avoindata.updater.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrakediaLiikennepaikkaServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TrakediaLiikennepaikkaService service;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws Exception {
        service = new TrakediaLiikennepaikkaService();
        webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        setField("webClient", webClient);
        setField("liikennepaikatUrl", "nodes");
        setField("liikennepaikanosatUrl", "parts");
    }

    @Test
    void givenCompleteNodeSourcesWhenRefreshingTwiceThenTheMergedMapIsCached() {
        // Given
        whenNodeResponse("nodes", node("AAA"));
        whenNodeResponse("parts", node("BBB"));
        clearInvocations(webClient);

        // When
        final InfraApiMapResult<JsonNode> first = service.getTrakediaLiikennepaikkaNodes();
        final InfraApiMapResult<JsonNode> second = service.getTrakediaLiikennepaikkaNodes();

        // Then the first call is a completed refresh, the second is served from cache
        assertThat(first.complete()).isTrue();
        assertThat(first.values()).containsKeys("AAA", "BBB");
        assertThat(first.cacheState()).isEqualTo(InfraApiMapResult.CacheState.MISS);
        assertThat(second.cacheState()).isEqualTo(InfraApiMapResult.CacheState.HIT);
        assertThat(second.values()).containsKeys("AAA", "BBB");
        verify(webClient, times(2)).get();
    }

    @Test
    void givenFirstNodeSourceFailsWhenRefreshingThenNoPartialMapIsReturnedOrCached() {
        // Given
        when(webClient.get().uri("nodes").retrieve().bodyToMono(JsonNode.class).block())
                .thenThrow(new IllegalStateException("nodes unavailable"));
        whenNodeResponse("parts", node("BBB"));

        // When
        final InfraApiMapResult<JsonNode> result = service.getTrakediaLiikennepaikkaNodes();

        // Then a failed refresh yields an explicit failure result, never a partial map
        assertThat(result.complete()).isFalse();
        assertThat(result.values()).isEmpty();
        assertThat(result.cacheState()).isEqualTo(InfraApiMapResult.CacheState.REFRESH_FAILED);
        assertThat(result.failure()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nodes unavailable");
    }

    @Test
    void givenEmptySuccessfulNodeSourceWhenFetchedThenItIsDifferentFromFailure() {
        // Given
        whenNodeResponse("nodes", MAPPER.readTree("[]"));

        // When
        final Map<String, JsonNode> emptyResult = service.fetchNodeMap("nodes");

        // Then
        assertThat(emptyResult).isEmpty();

        // Given
        when(webClient.get().uri("failed").retrieve().bodyToMono(JsonNode.class).block())
                .thenThrow(new IllegalStateException("request failed"));

        // When / Then
        assertThatThrownBy(() -> service.fetchNodeMap("failed"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("request failed");
    }

    @Test
    void givenARefreshFailsWhenRetriedThenEachCallAttemptsTheSourceAgain() {
        // Given
        when(webClient.get().uri("nodes").retrieve().bodyToMono(JsonNode.class).block())
                .thenThrow(new IllegalStateException("nodes unavailable"));
        whenNodeResponse("parts", node("BBB"));
        clearInvocations(webClient);

        // When
        final InfraApiMapResult<JsonNode> first = service.getTrakediaLiikennepaikkaNodes();
        final InfraApiMapResult<JsonNode> second = service.getTrakediaLiikennepaikkaNodes();

        // Then a failed refresh is never cached, so recovery needs no cool-off to expire
        assertThat(first.cacheState()).isEqualTo(InfraApiMapResult.CacheState.REFRESH_FAILED);
        assertThat(second.cacheState()).isEqualTo(InfraApiMapResult.CacheState.REFRESH_FAILED);
        verify(webClient, times(2)).get();
    }

    private void whenNodeResponse(final String url, final JsonNode response) {
        when(webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block()).thenReturn(response);
    }

    private static JsonNode node(final String abbreviation) {
        return MAPPER.readTree("[[{\"lyhenne\":\"" + abbreviation + "\",\"tunniste\":\"id-" + abbreviation + "\"}]]");
    }

    private void setField(final String name, final Object value) throws Exception {
        final Field field = TrakediaLiikennepaikkaService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}

