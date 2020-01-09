package fi.livi.rata.avoindata.updater.service.ruma;

public class RemoteTrackWorkNotificationStatus {
    public int id;
    public int version;

    public int getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public RemoteTrackWorkNotificationStatus(int id, int version) {
        this.id = id;
        this.version = version;
    }
}
