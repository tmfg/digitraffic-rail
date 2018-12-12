package fi.livi.rata.avoindata.updater.updaters.abstractup;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.amazonaws.xray.spring.aop.XRayTraced;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public abstract class AbstractPersistService<EntityType> implements XRayTraced {
    protected AtomicLong maxVersion = new AtomicLong(-1L);

    public abstract void clearEntities();
    public abstract void addEntities(List<EntityType> entities);
    public abstract List<EntityType> updateEntities(List<EntityType> entities);
    public abstract Long getMaxVersion();
}
