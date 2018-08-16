package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.dao.cause.CategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.CauseRepository;
import fi.livi.rata.avoindata.common.dao.cause.DetailedCategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.ThirdCategoryCodeRepository;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CauseFactory {

    @Autowired
    private CauseRepository causeRepository;

    @Autowired
    private CategoryCodeRepository categoryCodeRepository;

    @Autowired
    private DetailedCategoryCodeRepository detailedCategoryCodeRepository;

    @Autowired
    private ThirdCategoryCodeRepository thirdCategoryCodeRepository;

    public Cause create(TimeTableRow timeTableRow) {
        ThirdCategoryCode thirdCategoryCode = new ThirdCategoryCode();
        thirdCategoryCode.thirdCategoryCode = "3 koodi";
        thirdCategoryCode.thirdCategoryName = "3 koodin nimi";
        thirdCategoryCode.description = "3 koodin selitys";
        thirdCategoryCode.validFrom = LocalDate.of(2017, 1, 1);
        thirdCategoryCode.id = 3L;
        DetailedCategoryCode detailedCategoryCode = new DetailedCategoryCode();
        detailedCategoryCode.detailedCategoryCode = "2 koodi";
        detailedCategoryCode.detailedCategoryName = "2 koodin nimi";
        detailedCategoryCode.validFrom = LocalDate.of(2017, 1, 2);
        detailedCategoryCode.id = 2L;
        CategoryCode categoryCode = new CategoryCode();
        categoryCode.categoryCode = "1 koodi";
        categoryCode.categoryName = "1 koodin nimi";
        categoryCode.validFrom = LocalDate.of(2017, 1, 3);
        categoryCode.id = 1L;

        detailedCategoryCode.categoryCode = categoryCode;
        thirdCategoryCode.detailedCategoryCode = detailedCategoryCode;

        categoryCode = categoryCodeRepository.save(categoryCode);
        detailedCategoryCode = detailedCategoryCodeRepository.save(detailedCategoryCode);
        thirdCategoryCode = thirdCategoryCodeRepository.save(thirdCategoryCode);

        final Cause cause = new Cause();
        cause.thirdCategoryCode = thirdCategoryCode;
        cause.detailedCategoryCode = detailedCategoryCode;
        cause.categoryCode = categoryCode;
        cause.timeTableRow = timeTableRow;
        timeTableRow.causes.add(cause);

        return causeRepository.save(cause);

    }
}
