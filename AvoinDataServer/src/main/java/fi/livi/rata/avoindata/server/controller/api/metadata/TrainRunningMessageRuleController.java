package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRuleRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
public class TrainRunningMessageRuleController extends AMetadataController {
    @Autowired
    private TrainRunningMessageRuleRepository timeTableRowActivationRepository;

    @ApiOperation("Returns list of train running message rules")
    @RequestMapping(value = "train-running-message-rules", method = RequestMethod.GET)
    public List<TrainRunningMessageRule> getTrainRunningMessageRules(HttpServletResponse response) {
        final List<TrainRunningMessageRule> items = timeTableRowActivationRepository.findAll();
        setCache(response, items);
        return items;
    }
}
