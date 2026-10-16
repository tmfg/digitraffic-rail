package fi.livi.rata.avoindata.updater.service.stopsector;

import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MissingSectorCounter {
    private final Map<String, Integer> missingSectors = new HashMap<>();
    private final Object lock = new Object();

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MissingSectorCounter.class);

    public void addMissingSector(final String sectorIdentifier) {
        synchronized (lock) {
            missingSectors.merge(sectorIdentifier, 1, Integer::sum);
        }
    }

    public void logMissingSectors() {
        drainMissingSectors().forEach((missingSector, count) ->
                log.info("method=logMissingSectors missingSector={} count={}", missingSector, count));
    }

    public Map<String, Integer> drainMissingSectors() {
        synchronized (lock) {
            final Map<String, Integer> snapshot = new HashMap<>(missingSectors);
            missingSectors.clear();
            return snapshot;
        }
    }
}
