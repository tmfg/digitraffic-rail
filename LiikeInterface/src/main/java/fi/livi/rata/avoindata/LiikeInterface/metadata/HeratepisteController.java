package fi.livi.rata.avoindata.LiikeInterface.metadata;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Heratepiste;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.HeratepisteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class HeratepisteController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private HeratepisteRepository heratepisteRepository;

    @RequestMapping("/avoin/train-running-message-rules")
    @ResponseBody
    @JsonView({HeratepisteController.class})
    public List<Heratepiste> getData() {
        final List<Heratepiste> data = heratepisteRepository.findAll();
        log.info("Retrieved activation point data");
        return data;
    }
}