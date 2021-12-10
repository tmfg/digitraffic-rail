package fi.livi.rata.avoindata.LiikeInterface.metadata;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Operaattori;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.OperaattoriRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class OperaattoriController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private OperaattoriRepository operaattoriRepository;

    @RequestMapping("/avoin/operators")
    @ResponseBody
    @JsonView({OperaattoriController.class})
    public List<Operaattori> getOperators() {
        final List<Operaattori> operators = operaattoriRepository.findAll();
        log.info("Retrieved operator data");
        return operators;
    }
}
