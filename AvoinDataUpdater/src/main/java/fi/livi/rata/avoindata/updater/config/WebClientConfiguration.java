package fi.livi.rata.avoindata.updater.config;

import java.time.Duration;

import javax.net.ssl.SSLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfiguration {
    private static final String DIGITRAFFIC_USER = "Updater/rata.digitraffic.fi";

    public static final Duration BLOCK_DURATION = Duration.ofSeconds(30);

    @Bean
    public HttpClient defaultHttpClient(final @Value("${updater.http.connectionTimoutMillis:30000}") long connectionTimeOutMs) throws SSLException {
        // do not reuse connections with NewConnectionProvider
        return HttpClient.create(ConnectionProvider.newConnection())
                .responseTimeout(Duration.ofMillis(connectionTimeOutMs))
                // add connection close
                .headers(headers -> headers.add(HttpHeaders.CONNECTION, "close"))
                // add digitraffic-user header
                .headers(headers -> headers.add("Digitraffic-User", DIGITRAFFIC_USER))
                .keepAlive(false)
                .followRedirect(true)
                .compress(true);
    }

    @Bean
    public WebClient webClient(final HttpClient defaultHttpClient,
                               final ObjectMapper objectMapper) {

        // more memory for default web-client
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(defaultHttpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs
                                .defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                        .codecs(codecs -> codecs
                                .defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper)))
                        .build())
                .build();
    }

    @Bean
    public WebClient ripaWebClient(final WebClient webClient,
                                   final @Value("${updater.reason.api-key}") String apiKey,
                                   final @Value("${updater.liikeinterface-url}") String liikeInterfaceUrl) {
        return webClient.mutate()
                // manually add / in the end for the WebClient
                .baseUrl(liikeInterfaceUrl + "/")
                .defaultHeader("API-KEY", apiKey)
                .build();
    }
}
