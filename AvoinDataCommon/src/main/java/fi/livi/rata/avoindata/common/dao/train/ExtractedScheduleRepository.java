package fi.livi.rata.avoindata.common.dao.train;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.train.ExtractedSchedule;

@Repository
public interface ExtractedScheduleRepository extends CustomGeneralRepository<ExtractedSchedule, Long> {

    @Query("SELECT e FROM ExtractedSchedule e WHERE e.trainId.departureDate BETWEEN :start AND :end")
    List<ExtractedSchedule> findByDepartureDateBetween(LocalDate start, LocalDate end);
}
