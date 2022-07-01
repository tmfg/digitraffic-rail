package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.metadata.OperatorRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
public class OperatorController extends AMetadataController {
    @Autowired
    private OperatorRepository operatorRepository;

    @Operation(summary = "Returns list of operators",
               responses = { @ApiResponse(content = @Content(
                       mediaType = "application/json",
                       array = @ArraySchema(schema = @Schema(implementation = Operator.class)))) })
    @RequestMapping(value = "operators", method = RequestMethod.GET)
    public List<Operator> getOperators(HttpServletResponse response) {
        final List<Operator> output = operatorRepository.findAllAndFetchTrainNumbers();
        setCache(response, output);
        return output;
    }
}
