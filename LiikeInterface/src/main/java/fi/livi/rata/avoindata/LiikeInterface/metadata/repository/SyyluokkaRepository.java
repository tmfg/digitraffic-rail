package fi.livi.rata.avoindata.LiikeInterface.metadata.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Syyluokka;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyyluokkaRepository extends JpaRepository<Syyluokka, Long> {

    @Query("select sl from Syyluokka sl where " +
            "sl.voimassaAlkupvm < CURRENT_DATE or CURRENT_DATE between sl.voimassaAlkupvm and sl.voimassaLoppupvm")
    List<Syyluokka> findActiveSyyluokkas();
}
