package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.*;

import static fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController.TRAINS_TO_FETCH_PER_QUERY;

@Controller
public class OldTrainController {
    private Logger log = LoggerFactory.getLogger(OldTrainController.class);

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Autowired
    private JunapaivaPostProcessService junapaivaPostProcessService;


    @RequestMapping(value = "/avoin/old-trains", method = RequestMethod.POST)
    @ResponseBody
    @JsonView(JunapaivaController.class)
    public List<Junapaiva> getChangedJunapaivas(@RequestBody HashMap<String, Object> input) {
        Map<String, Long> versions = (Map<String, Long>) input.get("versions");

        final List<Object[]> date = junapaivaRepository.findJunapaivaVersions(LocalDate.parse(input.get("date").toString()),versions.keySet());

        List<JunapaivaPrimaryKey> keys = getChangedJunapaivaIds(versions, date);

        final List<Junapaiva> junapaivas = getJunapaivas(keys);

        return junapaivas;
    }

    private List<Junapaiva> getJunapaivas(final List<JunapaivaPrimaryKey> keys) {
        List<Junapaiva> trainsToReturn = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += TRAINS_TO_FETCH_PER_QUERY) {
            trainsToReturn.addAll(new HashSet<Junapaiva>(
                    junapaivaRepository.findByPrimaryKeys(keys.subList(i, Math.min(keys.size(), i + TRAINS_TO_FETCH_PER_QUERY)))));
        }

        final List<Junapaiva> junapaivas = junapaivaPostProcessService.postProcess(trainsToReturn);

        for (final Junapaiva junapaiva : junapaivas) {
            log.info("Returning changed train: {}", junapaiva.id);
        }
        return junapaivas;
    }

    private List<JunapaivaPrimaryKey> getChangedJunapaivaIds(final Map<String, Long> versions, final List<Object[]> date) {
        List<JunapaivaPrimaryKey> keys = new ArrayList<>();
        for (final Object[] train : date) {
            String junanumero = (String) train[0];
            LocalDate lahtopvm = (LocalDate) train[1];
            Long version = (Long) train[2];

            final Long remoteVersion = versions.get(junanumero);

            if (remoteVersion == null || remoteVersion < version) {
                final JunapaivaPrimaryKey id = new JunapaivaPrimaryKey(junanumero,lahtopvm);
                keys.add(id);
            }
        }
        return keys;
    }
}
