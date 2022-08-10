package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRuleRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class TrainRunningMessageRuleController extends AMetadataController {
    @Autowired
    private TrainRunningMessageRuleRepository timeTableRowActivationRepository;

    @Operation(summary = "Returns list of train running message rules")
    @RequestMapping(value = "train-running-message-rules", method = RequestMethod.GET)
    public List<TrainRunningMessageRule> getTrainRunningMessageRules(HttpServletResponse response) {
        final List<TrainRunningMessageRule> items = timeTableRowActivationRepository.findAll();
        setCache(response, items);
        return items;
    }
}
