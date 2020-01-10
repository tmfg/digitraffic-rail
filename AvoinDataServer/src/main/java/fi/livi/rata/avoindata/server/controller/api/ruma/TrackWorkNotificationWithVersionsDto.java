package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;

import java.util.List;

public class TrackWorkNotificationWithVersionsDto {

    public final int id;
    public final List<TrackWorkNotification> versions;

    public TrackWorkNotificationWithVersionsDto(int id, final List<TrackWorkNotification> versions) {
        this.id = id;
        this.versions = versions;
    }

}
