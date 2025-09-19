package fi.livi.rata.avoindata.updater.controllers;

import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClient;

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

        final StopWatch getIsUpToDates = StopWatch.createStarted();
        final Map<LastUpdateService.LastUpdatedType, IsUpToDateService.IsToUpToDateDto> isUpToDates = isUpToDateService.getIsUpToDates();
        getIsUpToDates.stop();

        log.info("method=getLastUpdated tookMs={} ", getIsUpToDates.getTime());
        isUpToDates.forEach((key, value) -> {
            if (!value.isUpToDate) {
                log.error("method=getLastUpdated data was outdated type={} lastUpdated={} limit={} sinceUpdate={} isUpToDate={}",
                        key, value.lastUpdated, value.alarmLimit, value.durationSinceLastUpdate, value.isUpToDate);
            }
        });
        return isUpToDates;
    }
}
