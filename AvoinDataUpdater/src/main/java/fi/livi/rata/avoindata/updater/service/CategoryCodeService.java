package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.cause.CategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.DetailedCategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.ThirdCategoryCodeRepository;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CategoryCodeService {
    @Autowired
    private CategoryCodeRepository categoryCodeRepository;

    @Autowired
    private DetailedCategoryCodeRepository detailedCategoryCodeRepository;

    @Autowired
    private ThirdCategoryCodeRepository thirdCategoryCodeRepository;

    @Transactional
    public void update(final CategoryCode[] categoryCodes) {
        detailedCategoryCodeRepository.deleteAllInBatch();
        categoryCodeRepository.deleteAllInBatch();
        thirdCategoryCodeRepository.deleteAllInBatch();

        categoryCodeRepository.persist(Arrays.asList(categoryCodes));

        List<DetailedCategoryCode> detailedCategoryCodes = new ArrayList<>();
        List<ThirdCategoryCode> thirdCategoryCodes = new ArrayList<>();
        for (final CategoryCode categoryCode : categoryCodes) {
            detailedCategoryCodes.addAll(categoryCode.detailedCategoryCodes);

            for (final DetailedCategoryCode detailedCategoryCode : detailedCategoryCodes) {
                thirdCategoryCodes.addAll(detailedCategoryCode.thirdCategoryCodes);
            }
        }

        detailedCategoryCodeRepository.persist(detailedCategoryCodes);
        thirdCategoryCodeRepository.persist(thirdCategoryCodes);
    }
}
