package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.updater.service.CategoryCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CategoryCodeUpdater extends AEntityUpdater<CategoryCode[]> {
    @Autowired
    private CategoryCodeService CategoryCodeService;

    //Every midnight 1:11
    @Override
    @Scheduled(cron = "0 1 11 * * ?")
    protected void update() {
        doUpdate("category-codes", CategoryCodeService::update, CategoryCode[].class);
    }
}
