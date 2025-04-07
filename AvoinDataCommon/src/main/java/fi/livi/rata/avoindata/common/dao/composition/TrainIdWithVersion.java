package fi.livi.rata.avoindata.common.dao.composition;

import java.time.LocalDate;

import javax.annotation.Nonnull;

public interface TrainIdWithVersion {
    long getTrainNumber();
    @Nonnull
    LocalDate getDepartureDate();
    long getVersion();
}
