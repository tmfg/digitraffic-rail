package fi.livi.rata.avoindata.LiikeInterface.metadata;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Syyluokka;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.SyyluokkaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class SyyluokkaController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SyyluokkaRepository syyluokkaRepository;

    @RequestMapping("/avoin/category-codes")
    @ResponseBody
    @JsonView({SyyluokkaController.class})
    public List<Syyluokka> getEntities() {
        final List<Syyluokka> entities = syyluokkaRepository.findAll();
        log.info("Retrieved categoryCodes data");
        return entities;
    }
}