package fi.livi.rata.avoindata.common.dao.cause;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepository;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;

@Repository
public interface CategoryCodeRepository extends CustomGeneralRepository<CategoryCode, String> {
    @Query("select cc from CategoryCode cc " +
            "   where (cc.validFrom < current_date and cc.validTo is null) or current_date between cc.validFrom and cc.validTo " +
            "   order by cc.categoryCode")
    List<CategoryCode> findActiveCategoryCodes();
}
