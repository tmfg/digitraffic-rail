package fi.livi.rata.avoindata.updater.service;

import static fi.livi.rata.avoindata.updater.config.WebClientConfiguration.BLOCK_DURATION;

import java.util.HashMap;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.digitraffic.common.util.StringUtil;
import reactor.core.publisher.Mono;


@Service
public class RipaService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final WebClient ripaWebClient;

    private final RestTemplate ripaRestTemplate;

    private final String liikeInterfaceUrl;
    private final String kojuApiUrl;

    public RipaService(final WebClient ripaWebClient, final RestTemplate ripaRestTemplate,
                       final @Value("${updater.liikeinterface-url}") String liikeInterfaceUrl,
                       final @Value("${updater.koju-api-url}") String kojuApiUrl) {
        this.ripaWebClient = ripaWebClient;
        this.ripaRestTemplate = ripaRestTemplate;
        this.liikeInterfaceUrl = liikeInterfaceUrl;
        this.kojuApiUrl = kojuApiUrl;
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipa(final String path, final Class<ENTITYTYPE> clazz) {
        log.info("method=getFromRipa Fetching from api={}/{} type={}", liikeInterfaceUrl, path, clazz.getSimpleName());
        try {
            return ripaWebClient.get().uri(path).retrieve().bodyToMono(clazz).block(BLOCK_DURATION);
        } catch (final Exception e) {
            log.error("method=getFromRipa Fetching from api={}/{} type={} failed with error {}",
                    liikeInterfaceUrl, path, clazz.getSimpleName(), e.getMessage());
            throw e;
        }
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipa(final String path, final Class<ENTITYTYPE> clazz, final String acceptHeader) {
        log.info("method=getFromRipa Fetching from api={}/{} type={} acceptHeader={}", liikeInterfaceUrl, path, clazz.getSimpleName(), acceptHeader);
        try {
            return ripaWebClient.get().uri(path).header(HttpHeaders.ACCEPT, acceptHeader).retrieve().bodyToMono(clazz).block(BLOCK_DURATION);
        } catch (final Exception e) {
            log.error("method=getFromRipa Fetching from api={}/{} type={} acceptHeader={} failed with error {}",
                    liikeInterfaceUrl, path, clazz.getSimpleName(), acceptHeader, e.getMessage());
            throw e;
        }
    }

    public <ENTITYTYPE> ENTITYTYPE getFromRipaRestTemplate(final String path, final Class<ENTITYTYPE> clazz) {
        final String finalPath = String.format("%s/%s", liikeInterfaceUrl, path);

        log.info("method=getFromRipaRestTemplate Fetching from api={} type={}", finalPath, clazz.getSimpleName());
        try {
            return ripaRestTemplate.getForObject(finalPath, clazz);
        } catch (final Exception e) {
            log.error("method=getFromRipaRestTemplate Fetching from api={} type={} failed with error {}",
                    finalPath, clazz.getSimpleName(), e.getMessage());
            throw e;
        }
    }

    public <ENTITYTYPE> ResponseWithVersion<ENTITYTYPE> getFromRipaRestTemplateWithVersion(final String path, final Class<ENTITYTYPE> clazz) {
        final String finalPath = String.format("%s/%s", liikeInterfaceUrl, path);

        log.info("method=getFromRipaRestTemplateWithVersion Fetching from api={} type={}", finalPath, clazz.getSimpleName());
        try {
            final ResponseEntity<ENTITYTYPE> response = ripaRestTemplate.getForEntity(finalPath, clazz);
            final String versionHeader = response.getHeaders().getFirst("fira-data-version");
            final Long version = versionHeader != null ? Long.parseLong(versionHeader) : null;
            return new ResponseWithVersion<>(response.getBody(), version);
        } catch (final Exception e) {
            log.error("method=getFromRipaRestTemplateWithVersion Fetching from api={} type={} failed with error {}",
                    finalPath, clazz.getSimpleName(), e.getMessage());
            throw e;
        }
    }

    public record ResponseWithVersion<T>(T body, Long version) {}

    public <ENTITYTYPE> ENTITYTYPE getFromKojuApiRestTemplate(final String path, final Class<ENTITYTYPE> clazz) {
        return getFromKojuApiRestTemplate(path, clazz, null);
    }

    public <ENTITYTYPE> ENTITYTYPE getFromKojuApiRestTemplate(final String path, final Class<ENTITYTYPE> clazz, final ETagRef eTagRef) {
        final String finalPath = StringUtil.format("{}/{}", kojuApiUrl, path);
        log.info("method=getFromKojuApiRestTemplate Fetching from api={} type={} etag={}", finalPath, clazz.getSimpleName(), eTagRef);
        try {
            final HttpHeaders headers = new HttpHeaders();
            if (eTagRef != null && eTagRef.getETag() != null) {
                headers.set(HttpHeaders.IF_NONE_MATCH, eTagRef.getETag());
            }
            final HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            final ResponseEntity<ENTITYTYPE> response = ripaRestTemplate.exchange(finalPath, HttpMethod.GET, requestEntity, clazz);
            if (eTagRef != null) {
                eTagRef.update(response);
            }
            return response.getBody();
        } catch (final Exception e) {
            log.error("method=getFromKojuApiRestTemplate Fetching from api={} type={} etag={} failed with error {}",
                    finalPath, clazz.getSimpleName(), eTagRef, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public <ENTITYTYPE> ENTITYTYPE postToRipa(final String path, final HashMap<?,?> parts, final Class<ENTITYTYPE> clazz) {
        return ripaWebClient.post().uri(path)
                .body(Mono.just(parts), HashMap.class)
                .retrieve().bodyToMono(clazz).block();

    }

    public static class ETagRef {
        private String eTag;
        private final Logger logger;

        public ETagRef(final String etag, final Logger logger) {
            this.eTag = etag;
            this.logger = logger;
        }

        public String getETag() {
            return eTag;
        }

        public <ENTITYTYPE> void update(final ResponseEntity<ENTITYTYPE> response) {
            if (response.getHeaders().get(HttpHeaders.ETAG) != null) {
                final String newETag = Objects.requireNonNull(response.getHeaders().get(HttpHeaders.ETAG)).getFirst();
                if (!Objects.equals(eTag, newETag)) {
                    if (logger != null) {
                        logger.debug("method=update Update ETagRef from {} to {}", eTag, newETag);
                    }
                    eTag = newETag;
                }
            } else {
                eTag = null;
            }
        }

        public void reset() {
            eTag = null;
        }

        @Override
        public String toString() {
            return eTag;
        }
    }

}