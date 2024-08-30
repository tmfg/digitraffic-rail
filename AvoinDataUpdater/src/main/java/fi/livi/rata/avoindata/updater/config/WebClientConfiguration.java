package fi.livi.rata.avoindata.updater.config;

import java.time.Duration;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfiguration {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String DIGITRAFFIC_USER = "Updater/rata.digitraffic.fi";

    public static final Duration BLOCK_DURATION = Duration.ofSeconds(30);

    public HttpClient createInsecureHttpClient(final long connectionTimeOutMs) throws SSLException {
        log.info("Creating insecure HTTP client");
        final SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        // do not reuse connections with NewConnectionProvider
        return HttpClient.create(ConnectionProvider.newConnection())
                .responseTimeout(Duration.ofMillis(connectionTimeOutMs))
                .secure(sslSpec -> sslSpec.sslContext(sslContext))
                // add connection close
                .headers(headers -> headers.add(HttpHeaders.CONNECTION, "close"))
                // add digitraffic-user header
                .headers(headers -> headers.add("Digitraffic-User", DIGITRAFFIC_USER))
                .keepAlive(false)
                .followRedirect(true)
                .compress(true);
    }

    public HttpClient createSecureHttpClient(final long connectionTimeOutMs) {
        log.info("Creating secure HTTP client");
        // do not reuse connections with NewConnectionProvider
        return HttpClient.create(ConnectionProvider.newConnection())
                .responseTimeout(Duration.ofMillis(connectionTimeOutMs))
                .secure()
                // add connection close
                .headers(headers -> headers.add(HttpHeaders.CONNECTION, "close"))
                // add digitraffic-user header
                .headers(headers -> headers.add("Digitraffic-User", DIGITRAFFIC_USER))
                .keepAlive(false)
                .followRedirect(true)
                .compress(true);
    }

    @Bean
    public HttpClient defaultHttpClient(final @Value("${updater.http.connectionTimoutMillis:30000}") long connectionTimeOutMs,
                                        @Value("${updater.validate-ripa-cert:true}")
                                        final boolean validateRipaCertificate) throws SSLException {
        /*
           SSL certificate validation needs to be disabled locally for the application to be able
           to access RIPA when connecting via SSM.
        */
        if (validateRipaCertificate) {
            return createSecureHttpClient(connectionTimeOutMs);
        } else {
            return createInsecureHttpClient(connectionTimeOutMs);
        }
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
