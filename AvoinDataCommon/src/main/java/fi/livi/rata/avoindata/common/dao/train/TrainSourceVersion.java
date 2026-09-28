package fi.livi.rata.avoindata.common.dao.train;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

public interface TrainSourceVersion {
    TrainId getId();

    Long getSourceVersion();
}
