package fi.livi.rata.avoindata.LiikeInterface.metadata;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Aikataulukausi;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.AikataulukausiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collection;

@Controller
public class AikataulukausiController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AikataulukausiRepository aikataulukausiRepository;

    @RequestMapping("/avoin/timetableperiods")
    @ResponseBody
    @JsonView(AikataulukausiController.class)
    public Collection<Aikataulukausi> get() {
        return aikataulukausiRepository.findAllAikataulukausi();
    }
}
