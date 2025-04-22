package fi.livi.rata.avoindata.common.dao.stopsector;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.stopsector.StopSectorQueueItem;

import java.util.List;

public interface StopSectorQueueItemRepository extends CustomGeneralRepository<StopSectorQueueItem, TrainId> {
    List<StopSectorQueueItem> findAlLByOrderByCreated();
}
