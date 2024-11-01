package fi.livi.rata.avoindata.common.dao.cause;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;

@Repository
public interface DetailedCategoryCodeRepository extends CustomGeneralRepository<DetailedCategoryCode, String> {
    @Query("select dcc from DetailedCategoryCode dcc " +
            "   where (dcc.validFrom < current_date and dcc.validTo is null) or current_date between dcc.validFrom and dcc.validTo" +
            "   order by dcc.detailedCategoryCode")
    List<DetailedCategoryCode> findActiveDetailedCategoryCodes();
}
