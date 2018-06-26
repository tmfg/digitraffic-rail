package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.common.dao.metadata.OperatorRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
public class OperatorController extends AMetadataController {
    @Autowired
    private OperatorRepository operatorRepository;

    @ApiOperation("Returns list of operators")
    @RequestMapping(value = "operators", method = RequestMethod.GET)
    public List<Operator> getOperators(HttpServletResponse response) {
        final List<Operator> output = operatorRepository.findAllAndFetchTrainNumbers();
        setCache(response, output);
        return output;
    }
}
