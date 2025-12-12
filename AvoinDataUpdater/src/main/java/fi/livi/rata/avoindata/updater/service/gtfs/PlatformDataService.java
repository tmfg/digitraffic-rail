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

    /*
    Here we map platform data from Infra-API to specific "liikennepaikanOsa" or "rautatieliikennepaikka" objects (these objects are part of the "Trakedia" data, also fetched from Infra-API).
    Rautatieliikennepaikkas and liikennepaikanOsas are in turn mapped to station short codes, which are identifiers from LIIKE interface and should mostly correspond to stations as they exist in Infra-API/Trakedia data as rautatieliikennepaikkas or liikennepaikanOsas.

    However, there is no absolute link between platforms and liikennepaikkas in Infra-API, so we might or might not match a station to its platforms based on the value of "liikennepaikanOsa" or "rautatieliikennepaikka".

    "liikennepaikanOsa", when it has a non-null value for a platform in Infra-API, should normally point to the correct station represented as a liikennepaikanOsa in Infra-API and matched to a Digitraffic station via the related short form of the station's name.

    In some cases, "rautatieliikennepaikka" in the platform data will contain the reference to the appropriate rautatieliikennepaikka representing the station in Infra-API. In these cases "liikennepaikanOsa" is probably null?

    If a platform has values for both "liikennepaikanOsa" and "rautatieliikennepaikka", we pick liikennepaikanOsa as the identifier to map the
    associated liikennepaikanOsa in Infra-API to a Digitraffic station short code.
     */

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
