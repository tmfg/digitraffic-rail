package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma;

import static fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController.TRAINS_TO_FETCH_PER_QUERY;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.SyytietoRepository;

@Controller
public class OldTrainController {
    private Logger log = LoggerFactory.getLogger(OldTrainController.class);

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Autowired
    private SyytietoRepository syytietoRepository;

    @Autowired
    private JunapaivaPostProcessService junapaivaPostProcessService;

    @RequestMapping(value = "/avoin/old-trains", method = RequestMethod.POST)
    @ResponseBody
    @JsonView(JunapaivaController.class)
    public List<Junapaiva> getChangedJunapaivas(@RequestBody HashMap<String, Object> input) {
        Map<String, Long> versions = (Map<String, Long>) input.get("versions");

        Set<String> junanumeros = versions.keySet();
        LocalDate departureDate = LocalDate.parse(input.get("date").toString());

        List<JunapaivaVersionKey> junapaivaVersions = getJunapaivaVersions(junanumeros, departureDate);
        List<JunapaivaPrimaryKey> junapaivaKeys = getChangedJunapaivaIds(versions, junapaivaVersions);

        return getJunapaivas(junapaivaKeys);
    }

    private List<JunapaivaVersionKey> getJunapaivaVersions(Set<String> junanumeros, LocalDate departureDate) {
        Collector<JunapaivaVersionKey, ?, Map<String, JunapaivaVersionKey>> junapaivaVersionKeyMapCollector = Collectors.toMap(s -> String.format("%s_%s", s.trainNumber, s.departureDate), s -> s);
        final Map<String, JunapaivaVersionKey> junapaivaVersions = junapaivaRepository.findMaxVersions(departureDate, junanumeros).stream()
                .map(s -> new JunapaivaVersionKey(s))
                .collect(junapaivaVersionKeyMapCollector);
        final Map<String, JunapaivaVersionKey> syytietoVersions = syytietoRepository.findMaxVersions(departureDate, junanumeros).stream()
                .map(s -> new JunapaivaVersionKey(s))
                .collect(junapaivaVersionKeyMapCollector);

        List<JunapaivaVersionKey> output = new ArrayList<>();
        for (String key : junapaivaVersions.keySet()) {
            JunapaivaVersionKey jupaVersionKey = junapaivaVersions.get(key);
            JunapaivaVersionKey syytietoVersionKey = syytietoVersions.get(key);

            if (syytietoVersionKey != null) {
                jupaVersionKey.version = Math.max(jupaVersionKey.version, syytietoVersionKey.version);
            }
            output.add(jupaVersionKey);
        }
        return output;
    }

    private List<Junapaiva> getJunapaivas(final List<JunapaivaPrimaryKey> keys) {
        List<Junapaiva> trainsToReturn = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += TRAINS_TO_FETCH_PER_QUERY) {
            trainsToReturn.addAll(new HashSet<>(
                    junapaivaRepository.findByPrimaryKeys(keys.subList(i, Math.min(keys.size(), i + TRAINS_TO_FETCH_PER_QUERY)))));
        }

        final List<Junapaiva> junapaivas = junapaivaPostProcessService.postProcess(trainsToReturn);

        for (final Junapaiva junapaiva : junapaivas) {
            log.info("Returning changed train: {}", junapaiva.id);
        }
        return junapaivas;
    }

    private List<JunapaivaPrimaryKey> getChangedJunapaivaIds(final Map<String, Long> versions, final List<JunapaivaVersionKey> versionKeys) {
        List<JunapaivaPrimaryKey> keys = new ArrayList<>();
        for (final JunapaivaVersionKey train : versionKeys) {

            final Long remoteVersion = versions.get(train.trainNumber);

            if (remoteVersion == null || remoteVersion < train.version) {
                final JunapaivaPrimaryKey id = new JunapaivaPrimaryKey(train.trainNumber, train.departureDate);
                keys.add(id);
            }
        }
        return keys;
    }

    private static class JunapaivaVersionKey {
        public JunapaivaVersionKey(Object[] rawOracleReply) {
            this.trainNumber = (String) rawOracleReply[0];
            this.departureDate = (LocalDate) rawOracleReply[1];
            this.version = (Long) rawOracleReply[2];
        }

        public String trainNumber;
        public LocalDate departureDate;
        public Long version;
    }
}
