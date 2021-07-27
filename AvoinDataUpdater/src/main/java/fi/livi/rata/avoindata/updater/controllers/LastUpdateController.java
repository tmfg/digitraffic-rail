package fi.livi.rata.avoindata.updater.controllers;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.isuptodate.IsUpToDateService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;

@Controller
public class LastUpdateController {
    @Autowired
    private IsUpToDateService isUpToDateService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DateProvider dateProvider;

    @RequestMapping("/last-updated")
    @ResponseBody
    public Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> getResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", String.format("max-age=%d, public", 1));

        Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> isUpToDates = isUpToDateService.getIsUpToDates();

        isUpToDates.put(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS_DUMP, createIsUpToDateForUrl(String.format("https://rata.digitraffic.fi/api/v1/train-locations/dumps/digitraffic-rata-train-locations-%s.zip", this.dateProvider.dateInHelsinki().minusDays(3)), Duration.ofHours(24 * 2)));

        return isUpToDates;
    }

    private IsUpToDateService.IsToUpToDateDto createIsUpToDateForUrl(String url, Duration limit) {
        HttpHeaders httpHeaders = this.restTemplate.headForHeaders(URI.create(url));
        ZonedDateTime lastModified = ZonedDateTime.ofInstant(Instant.ofEpochMilli(httpHeaders.getLastModified()), ZoneId.of("UTC"));
        Duration sinceUpdate = Duration.between(lastModified, ZonedDateTime.now());
        IsUpToDateService.IsToUpToDateDto isToUpToDateDto = new IsUpToDateService.IsToUpToDateDto(ZonedDateTime.now(), limit, sinceUpdate);
        return isToUpToDateDto;
    }
}
