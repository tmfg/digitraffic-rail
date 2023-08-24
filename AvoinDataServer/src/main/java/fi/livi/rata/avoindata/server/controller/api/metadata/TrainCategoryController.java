package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class TrainCategoryController extends AMetadataController {
    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Operation(summary = "Returns a list of train categories")
    @RequestMapping(value = "train-categories", method = RequestMethod.GET)
    public List<TrainCategory> getTrainCategories(HttpServletResponse response) {
        final List<TrainCategory> all = trainCategoryRepository.findAllCached();

        setCache(response, all);

        return all;
    }
}
