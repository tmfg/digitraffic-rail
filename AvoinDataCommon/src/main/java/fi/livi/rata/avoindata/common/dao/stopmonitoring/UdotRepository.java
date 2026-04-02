package fi.livi.rata.avoindata.common.dao.stopmonitoring;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.stopmonitoring.Udot;
import fi.livi.rata.avoindata.common.domain.stopmonitoring.UdotData;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface UdotRepository extends CustomGeneralRepository<Udot, String> {

    List<UdotData> findByModelUpdatedTimeIsNullOrderByModifiedDb();

    @Modifying
    @Query(value = "UPDATE rami_udot SET model_updated_time = CURRENT_TIMESTAMP WHERE id = :id AND modified_db = :modifiedDb", nativeQuery = true)
    void setModelUpdated(@Param("id") final Long id, @Param("modifiedDb") final ZonedDateTime modifiedDb);

    @Query(value = """
select attap_id, unknown_delay, unknown_track
from time_table_row ttr
where departure_date = ?1 and train_number = ?2
and (unknown_delay is not null or unknown_track is not null)
""", nativeQuery = true)
    List<Object[]> getRowsWithUdot(LocalDate departureDate, Long trainNumber);
}
