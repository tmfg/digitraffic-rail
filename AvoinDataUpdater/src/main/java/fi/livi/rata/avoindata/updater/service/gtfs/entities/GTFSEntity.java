package fi.livi.rata.avoindata.updater.service.gtfs.entities;

public abstract class GTFSEntity<B> {
    public GTFSEntity(B source) {
        this.source = source;
    }

    public B source;
}
