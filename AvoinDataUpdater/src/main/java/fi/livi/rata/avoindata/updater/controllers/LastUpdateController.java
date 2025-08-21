package fi.livi.rata.avoindata.updater.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.digitraffic.common.util.TimeUtil;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.isuptodate.IsUpToDateService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LastUpdateController {
    private static final Logger log = LoggerFactory.getLogger(LastUpdateController.class);

    @Autowired
    private IsUpToDateService isUpToDateService;

    @Autowired
    private WebClient webClient;


    @RequestMapping("/last-updated")
    @ResponseBody
    public Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> getLastUpdated(final HttpServletResponse response) {
        response.setHeader("Cache-Control", String.format("max-age=%d, public", 1));

        final Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> isUpToDates = isUpToDateService.getIsUpToDates();

        isUpToDates.put(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS_DUMP, createIsUpToDateForUrl(String.format("https://rata.digitraffic.fi/api/v1/train-locations/dumps/digitraffic-rata-train-locations-%s.zip", DateProvider.dateInHelsinki().minusDays(3)), Duration.ofHours(24 * 2)));

        isUpToDates.forEach((key, value) -> {
            if (!value.isUpToDate) {
                log.error("method=getLastUpdated data was outdated type={} lastUpdated={} limit={} sinceUpdate={} isUpToDate={}",
                        key, value.lastUpdated, value.alarmLimit, value.durationSinceLastUpdate, value.isUpToDate);
            }
        });
        return isUpToDates;
    }

    private IsUpToDateService.IsToUpToDateDto createIsUpToDateForUrl(final String url, final Duration limit) {
        try {
            final HttpHeaders httpHeaders = Objects.requireNonNull(webClient.head().uri(url).retrieve().toBodilessEntity().block()).getHeaders();
            final Instant lastModified = TimeUtil.toInstant(httpHeaders.getLastModified());
            final Duration sinceUpdate = Duration.between(lastModified, Instant.now());
            return new IsUpToDateService.IsToUpToDateDto(lastModified, limit, sinceUpdate);
        } catch (final HttpClientErrorException.TooManyRequests exception) {
            return new IsUpToDateService.IsToUpToDateDto(Instant.now(), limit, Duration.ofDays(0));
        } catch (final HttpClientErrorException.NotFound | NullPointerException exception) {
            return new IsUpToDateService.IsToUpToDateDto(null, limit, Duration.ofDays(30));
        }
    }
}
