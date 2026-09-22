package fi.livi.rata.avoindata.updater.service.infraapi.observability;

import java.util.Map;

/**
 * Source identity carried by every Infra API wide event. Defined once so the run event, the
 * per-segment failure events and the source-refresh events cannot drift apart.
 */
public final class InfraApiSource {

    private InfraApiSource() {
    }

    public static void addTo(final Map<String, Object> event) {
        event.put("rail.source.system", "DIGITRAFFIC");
        event.put("rail.source.api", "infra-api");
        event.put("rail.source.owner", "TRAKEDIA");
    }
}
