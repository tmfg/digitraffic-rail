package fi.livi.rata.avoindata.updater.controllers;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.isuptodate.IsUpToDateService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

@Controller
public class LastUpdateController {
    @Autowired
    private IsUpToDateService isUpToDateService;

    @Autowired
    private WebClient webClient;

    @Autowired
    private DateProvider dateProvider;

    @RequestMapping("/last-updated")
    @ResponseBody
    public Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> getResponse(final HttpServletResponse response) {
        response.setHeader("Cache-Control", String.format("max-age=%d, public", 1));

        final Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> isUpToDates = isUpToDateService.getIsUpToDates();

        isUpToDates.put(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS_DUMP, createIsUpToDateForUrl(String.format("https://rata.digitraffic.fi/api/v1/train-locations/dumps/digitraffic-rata-train-locations-%s.zip", this.dateProvider.dateInHelsinki().minusDays(3)), Duration.ofHours(24 * 2)));

        return isUpToDates;
    }

    private IsUpToDateService.IsToUpToDateDto createIsUpToDateForUrl(final String url, final Duration limit) {
        try {
            final HttpHeaders httpHeaders = webClient.head().uri(url).retrieve().toBodilessEntity().block().getHeaders();
            final ZonedDateTime lastModified = ZonedDateTime.ofInstant(Instant.ofEpochMilli(httpHeaders.getLastModified()), ZoneId.of("UTC"));
            final Duration sinceUpdate = Duration.between(lastModified, ZonedDateTime.now());
            final IsUpToDateService.IsToUpToDateDto isToUpToDateDto = new IsUpToDateService.IsToUpToDateDto(lastModified, limit, sinceUpdate);
            return isToUpToDateDto;
        } catch (final HttpClientErrorException.TooManyRequests exception) {
            return new IsUpToDateService.IsToUpToDateDto(ZonedDateTime.now(), limit, Duration.ofDays(0));
        } catch (final HttpClientErrorException.NotFound exception) {
            return new IsUpToDateService.IsToUpToDateDto(null, limit, Duration.ofDays(30));
        }
    }
}
