package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class TrainTypeController extends AMetadataController {
    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @Operation(summary = "Returns a list of train types")
    @RequestMapping(value = "train-types", method = RequestMethod.GET)
    public List<TrainType> getTrainTypes(HttpServletResponse response) {
        final List<TrainType> all = trainTypeRepository.findAll();

        Map<String, TrainType> trainTypeMap = new HashMap<>();
        for (final TrainType trainType : all) {
            trainTypeMap.put(trainType.name, trainType);
        }

        setCache(response,trainTypeMap.values());

        return new ArrayList<>(trainTypeMap.values());
    }
}
