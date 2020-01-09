package fi.livi.rata.avoindata.updater.service.ruma;

import java.util.Collections;
import java.util.List;

public class LocalTrackWorkNotificationStatus {

    public final int id;
    public final int minVersion;
    public final int maxVersion;

    public LocalTrackWorkNotificationStatus(int id, List<Integer> versions) {
        this.id = id;
        this.minVersion = Collections.min(versions);
        this.maxVersion = Collections.max(versions);
    }

    public int getId() {
        return id;
    }
}
