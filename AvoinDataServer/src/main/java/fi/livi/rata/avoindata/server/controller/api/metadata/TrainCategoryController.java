package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
public class TrainCategoryController extends AMetadataController {
    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @ApiOperation("Returns a list of train categories")
    @RequestMapping(value = "train-categories", method = RequestMethod.GET)
    public List<TrainCategory> getTrainCategories(HttpServletResponse response) {
        final List<TrainCategory> all = trainCategoryRepository.findAllCached();

        setCache(response, all);

        return all;
    }
}
