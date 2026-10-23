package fi.livi.rata.avoindata.common.domain.gtfs;

import java.util.List;

/**
 * File names of the realtime feeds stored in the gtfs table. Only the newest row of each is served, so older rows
 * are kept for a short time only.
 */
public final class RealtimeFeedFileNames {
    public static final String GTFS_RT_LOCATIONS = "gtfs-rt-locations";
    public static final String GTFS_RT_UPDATES = "gtfs-rt-updates";
    public static final String SIRI_VM = "siri-vm.xml";
    public static final String SIRI_ET = "siri-et.xml";

    public static final List<String> ALL = List.of(GTFS_RT_LOCATIONS, GTFS_RT_UPDATES, SIRI_VM, SIRI_ET);

    private RealtimeFeedFileNames() {
    }
}
