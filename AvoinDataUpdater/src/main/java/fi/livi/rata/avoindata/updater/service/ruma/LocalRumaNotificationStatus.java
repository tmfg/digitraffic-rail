package fi.livi.rata.avoindata.updater.service.ruma;

import java.util.Collections;
import java.util.List;

public class LocalRumaNotificationStatus {

    public final String id;
    public final long minVersion;
    public final long maxVersion;

    public LocalRumaNotificationStatus(String id, List<Long> versions) {
        this.id = id;
        this.minVersion = Collections.min(versions);
        this.maxVersion = Collections.max(versions);
    }

    public String getId() {
        return id;
    }
}
