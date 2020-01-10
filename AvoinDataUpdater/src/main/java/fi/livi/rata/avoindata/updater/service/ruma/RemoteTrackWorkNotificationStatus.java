package fi.livi.rata.avoindata.updater.service.ruma;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteTrackWorkNotificationStatus {
    public int id;
    public int version;

    public int getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    @JsonCreator
    public RemoteTrackWorkNotificationStatus(
            @JsonProperty("id") int id,
            @JsonProperty("version") int version)
    {
        this.id = id;
        this.version = version;
    }

}
