package fi.livi.rata.avoindata.updater.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class VersionedService<T> {
    protected AtomicLong maxVersion = new AtomicLong(-1L);

    public abstract void updateObjects(final List<T> objects);

    public abstract Long getMaxVersion();

    public void setInitialMaxVersion(Long version) {
        maxVersion.compareAndSet(-1,version);
    }
}
