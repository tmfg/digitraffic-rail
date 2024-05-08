package fi.livi.rata.avoindata.updater.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import fi.livi.rata.avoindata.common.domain.train.Train;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.HashMap;

import static fi.livi.rata.avoindata.updater.config.WebClientConfiguration.BLOCK_DURATION;

@Service
public class RipaService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final WebClient ripaWebClient;

    private final RestTemplate ripaRestTemplate;

    private final String liikeInterfaceUrl;

    private final ObjectMapper objectMapper;
    public RipaService(final WebClient ripaWebClient, final RestTemplate ripaRestTemplate,
                        final @Value("${updater.liikeinterface-url}") String liikeInterfaceUrl, final ObjectMapper objectMapper) {
        this.ripaWebClient = ripaWebClient;
        this.ripaRestTemplate = ripaRestTemplate;
        this.liikeInterfaceUrl = liikeInterfaceUrl;
        this.objectMapper = objectMapper;
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipa(final String path, final Class<ENTITYTYPE> clazz) {
        log.info("Fetching from {}", path);

        return ripaWebClient.get().uri(path).retrieve().bodyToMono(clazz).block(BLOCK_DURATION);
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipa(final String path, final Class<ENTITYTYPE> clazz, final String acceptHeader) {
        log.info("Fetching from {}", path);

        return ripaWebClient.get().uri(path).header(HttpHeaders.ACCEPT, acceptHeader).retrieve().bodyToMono(clazz).block(BLOCK_DURATION);
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipaRestTemplate(final String path, final Class<ENTITYTYPE> clazz) {
        final String finalPath = String.format("%s/%s", liikeInterfaceUrl, path);

        log.info("Fetching from {}", finalPath);

        return ripaRestTemplate.getForObject(finalPath, clazz);
    }

    public <ENTITYTYPE> ENTITYTYPE postToRipa(final String path, final HashMap parts, final Class<ENTITYTYPE> clazz) {
        return ripaWebClient.post().uri(path)
                .body(parts, HashMap.class)
                .retrieve().bodyToMono(clazz).block();

    }
}