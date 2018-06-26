package fi.livi.rata.avoindata.updater.service.recentlyseen;

import org.slf4j.Logger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class AbstractRecentlySeenEntityFilter<EntityType, KeyType> {
    private final Map<KeyType, ZonedDateTime> recentlySeenEntities = new HashMap<>();
    private Logger log = getLogger();

    public List<EntityType> filter(final List<EntityType> entities) {
        final List<EntityType> uniqueEntities = filterOutRecentlySeenEntities(entities);
        deleteOldEntities();

        return uniqueEntities;
    }

    public abstract ZonedDateTime getTimestamp(EntityType entity);

    public abstract KeyType getKey(EntityType entity);

    public abstract boolean isTooOld(ZonedDateTime entity);

    public abstract Logger getLogger();

    private List<EntityType> filterOutRecentlySeenEntities(final List<EntityType> entities) {
        List<EntityType> uniqueEntities = new ArrayList<>();
        for (final EntityType entity : entities) {
            final KeyType key = getKey(entity);
            final ZonedDateTime oldEntity = recentlySeenEntities.get(key);
            if (oldEntity == null) {
                uniqueEntities.add(entity);
                recentlySeenEntities.put(key, getTimestamp(entity));
            }
        }

        log.info("Entities total: {}, Unique: {}", entities.size(), uniqueEntities.size());

        return uniqueEntities;
    }

    private void deleteOldEntities() {
        final List<KeyType> toBeDeleted = new ArrayList<>();
        for (final KeyType key : recentlySeenEntities.keySet()) {
            if (isTooOld(recentlySeenEntities.get(key))) {
                toBeDeleted.add(key);
            }
        }

        for (final KeyType key : toBeDeleted) {
            recentlySeenEntities.remove(key);
        }

        log.debug("RecentlySeen entities: {}, Deleted: {}", recentlySeenEntities.size(), toBeDeleted.size());
    }
}
