package fi.livi.rata.avoindata.updater.service.ruma;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteTrackWorkNotificationStatus {
    public long id;
    public long version;

    public long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    @JsonCreator
    public RemoteTrackWorkNotificationStatus(
            @JsonProperty("id") long id,
            @JsonProperty("version") long version)
    {
        this.id = id;
        this.version = version;
    }

}
