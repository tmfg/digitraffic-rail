package fi.livi.rata.avoindata.updater.service.ruma;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteRumaNotificationStatus {
    public String id;
    public long version;

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    @JsonCreator
    public RemoteRumaNotificationStatus(
            @JsonProperty("id") String id,
            @JsonProperty("version") long version)
    {
        this.id = id;
        this.version = version;
    }

}
