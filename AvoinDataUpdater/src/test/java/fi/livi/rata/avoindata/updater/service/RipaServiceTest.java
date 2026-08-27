package fi.livi.rata.avoindata.updater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

/**
 * Pins the exception contract that {@code TrainLocationUpdater.fetchFromPala} depends on:
 *
 * <ul>
 *   <li>{@code WebClient.retrieve()} must turn a 4xx/5xx PALA response into a {@link WebClientResponseException} that
 *       carries the real HTTP status, and</li>
 *   <li>{@code Mono.block()} must propagate that exception <b>unwrapped</b> (it is an unchecked {@link RuntimeException},
 *       so {@code block()} does not wrap it), through {@link RipaService#getFromPalaAsString} which rethrows it as-is.</li>
 * </ul>
 *
 * <p>If a Spring/Reactor upgrade or a refactor away from {@code retrieve()} (e.g. to {@code exchangeToMono}) changes this
 * behavior, these tests fail so we know to revisit the {@code fetchFromPala} status-code handling.
 *
 * <p>Uses a stubbed {@link ExchangeFunction} instead of a network mock so the test exercises the real {@code retrieve()}
 * + {@code block()} code paths without an HTTP server.
 */
public class RipaServiceTest {

    private static RipaService ripaServiceReturning(final HttpStatus status, final String body) {
        final ExchangeFunction exchange = request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build());
        final WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        // Only palaWebClient (2nd arg) is exercised by getFromPalaAsString; RestTemplate is unused here.
        return new RipaService(webClient, webClient, null, "http://liike", "http://koju", "http://pala");
    }

    @Test
    public void serverErrorShouldThrowWebClientResponseExceptionWithRealStatus() {
        final RipaService service = ripaServiceReturning(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        final WebClientResponseException ex = assertThrows(WebClientResponseException.class,
                () -> service.getFromPalaAsString("0.2/yksikot.json"));

        // fetchFromPala reads exactly this: e.getStatusCode().value()
        assertEquals(500, ex.getStatusCode().value());
    }

    @Test
    public void clientErrorShouldThrowWebClientResponseExceptionWithRealStatus() {
        final RipaService service = ripaServiceReturning(HttpStatus.NOT_FOUND, "nope");

        final WebClientResponseException ex = assertThrows(WebClientResponseException.class,
                () -> service.getFromPalaAsString("0.2/yksikot.json"));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void successfulResponseShouldReturnBodyUnwrapped() {
        final RipaService service = ripaServiceReturning(HttpStatus.OK, "{\"1\":{}}");

        assertEquals("{\"1\":{}}", service.getFromPalaAsString("0.2/yksikot.json"));
    }
}
