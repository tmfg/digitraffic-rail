package fi.livi.rata.avoindata.updater.service.ruma;

import java.util.Collections;
import java.util.List;

public class LocalTrackWorkNotificationStatus {

    public final long id;
    public final long minVersion;
    public final long maxVersion;

    public LocalTrackWorkNotificationStatus(long id, List<Long> versions) {
        this.id = id;
        this.minVersion = Collections.min(versions);
        this.maxVersion = Collections.max(versions);
    }

    public long getId() {
        return id;
    }
}
