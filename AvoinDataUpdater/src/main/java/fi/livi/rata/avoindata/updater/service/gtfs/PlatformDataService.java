package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.PlatformData;

@Service
public class PlatformDataService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final InfraApiPlatformService infraApiPlatformService;
    private final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    public PlatformDataService(final InfraApiPlatformService infraApiPlatformService, final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService) {
        this.infraApiPlatformService = infraApiPlatformService;
        this.trakediaLiikennepaikkaService = trakediaLiikennepaikkaService;
    }

    public PlatformData getCurrentPlatformData() {
        final ZonedDateTime currentDate = DateProvider.nowInHelsinki().truncatedTo(ChronoUnit.SECONDS).withZoneSameInstant(ZoneId.of("UTC"));

        final Map<String, JsonNode> liikennePaikkaNodes = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes();

        final Map<String, List<InfraApiPlatform>> platformsByLiikennepaikkaIdPart =
                infraApiPlatformService.getPlatformsByLiikennepaikkaIdPart(currentDate, currentDate.plusDays(10));

        final Map<String, List<InfraApiPlatform>> platformsByStation = liikennePaikkaNodes.keySet()
                .stream()
                .collect(Collectors.toMap(
                        stationShortCode -> stationShortCode,
                        stationShortCode -> {
                            final String stationLiikennepaikkaId = liikennePaikkaNodes.get(stationShortCode).get(0).get("tunniste").asText();
                            final String stationLiikennepaikkaIdPart = InfraApiPlatformService.extractLiikennepaikkaIdPart(stationLiikennepaikkaId);
                            return new ArrayList<>(platformsByLiikennepaikkaIdPart.getOrDefault(stationLiikennepaikkaIdPart, Collections.emptyList()));
                        })
                );

        return new PlatformData(platformsByStation);
    }

}
