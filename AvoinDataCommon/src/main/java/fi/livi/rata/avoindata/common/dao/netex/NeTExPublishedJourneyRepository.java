package fi.livi.rata.avoindata.common.dao.netex;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;

@Repository
public interface NeTExPublishedJourneyRepository extends CustomGeneralRepository<NeTExPublishedJourney, Long> {
    @Query("select max(j.datasetVersion) from NeTExPublishedJourney j")
    Long getMaxDatasetVersion();

    /** Loads the journeys (with their tracks eagerly fetched) for a dataset version and set of operating days. */
    @Query("select distinct j from NeTExPublishedJourney j left join fetch j.tracks "
            + "where j.datasetVersion = :version and j.trainId.departureDate in :departureDates")
    List<NeTExPublishedJourney> findByDatasetVersionAndDepartureDatesFetchTracks(@Param("version") long version,
            @Param("departureDates") Collection<LocalDate> departureDates);

    @Modifying
    @Query("delete from NeTExPublishedJourney j where j.generatedAt < :cutoff")
    int deleteByGeneratedAtBefore(@Param("cutoff") ZonedDateTime cutoff);
}
