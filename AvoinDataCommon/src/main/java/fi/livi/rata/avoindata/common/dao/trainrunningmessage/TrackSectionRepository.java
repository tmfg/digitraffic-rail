package fi.livi.rata.avoindata.common.dao.trainrunningmessage;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;

@Repository
public interface TrackSectionRepository extends CustomGeneralRepository<TrackSection, Long> {
    @Query("select ts from TrackSection ts left join fetch ts.ranges ranges")
    List<TrackSection> findAllWithJoins();
}

