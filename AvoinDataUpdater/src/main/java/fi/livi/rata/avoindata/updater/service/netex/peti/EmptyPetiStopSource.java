package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback PetiStopSource returning an empty list.
 * Active when {@code updater.netex.peti.enabled=false} (local dev / test profiles
 * that don't want HTTP calls). Otherwise CachingPetiStopSource is active by default.
 */
@Component
@ConditionalOnProperty(name = "updater.netex.peti.enabled", havingValue = "false")
public class EmptyPetiStopSource implements PetiStopSource {

    @Override
    public List<PetiStop> getStops() {
        return List.of();
    }
}
