package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaTapahtuma;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JupaTapahtumaRepository;

@Controller
public class JunapaivaController {
    private Logger log = LoggerFactory.getLogger(JunapaivaController.class);

    @Value("${liikeinterface.maxVersion.query.returned.listSize:50000}")
    private int MAX_VERSION_QUERY_RETURNED_LIST_SIZE;

    public static final int TRAINS_TO_FETCH_PER_QUERY = 1000;

    @Autowired
    private JunapaivaRepository junapaivaRepository;

    @Autowired
    private JupaTapahtumaRepository jupaTapahtumaRepository;

    @Autowired
    private JunapaivaPostProcessService junapaivaPostProcessService;

    @RequestMapping(value = "/avoin/trains", params = "version")
    @ResponseBody
    @JsonView(JunapaivaController.class)
    public List<Junapaiva> getByVersion(@RequestParam Long version) throws InterruptedException {

        final ZonedDateTime now = ZonedDateTime.now();
        Collection<Junapaiva> junapaivas = new HashSet<>();

        final long maxVersion = jupaTapahtumaRepository.getMaxVersion(ZonedDateTime.now(ZoneId.of("Europe/Helsinki")).minusHours(18));

        List<JunapaivaPrimaryKey> junapaivaPrimaryKeysCasted = getChangedJunapaivasByVersion(version, maxVersion);

        if (junapaivaPrimaryKeysCasted.size() >= MAX_VERSION_QUERY_RETURNED_LIST_SIZE) {
            throw new IllegalArgumentException(String.format("Version %d is too old.", version));
        }
        if (!junapaivaPrimaryKeysCasted.isEmpty()) {
            for (int i = 0; i < junapaivaPrimaryKeysCasted.size(); i += TRAINS_TO_FETCH_PER_QUERY) {
                junapaivas.addAll(new HashSet<>(junapaivaRepository.findByPrimaryKeys(junapaivaPrimaryKeysCasted
                        .subList(i, Math.min(junapaivaPrimaryKeysCasted.size(), i + TRAINS_TO_FETCH_PER_QUERY)))));
            }
        }

        junapaivas = junapaivaPostProcessService.postProcess(new ArrayList<>(junapaivas));
        for (final Junapaiva junapaiva : junapaivas) {
            for (final JupaTapahtuma jupaTapahtuma : junapaiva.jupaTapahtumas) {
                jupaTapahtuma.version = maxVersion;
            }
        }

        log.info(String.format("Retrieved %d trains in %s ms (version %d -> %d)", junapaivas.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), version, maxVersion));

        return new ArrayList<>(junapaivas);
    }

    @RequestMapping(value = "/avoin/trains", params = "date")
    @ResponseBody
    @JsonView(JunapaivaController.class)
    public Collection<Junapaiva> getByDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {

        final ZonedDateTime now = ZonedDateTime.now();

        Collection<Junapaiva> junapaivas = new HashSet<>(junapaivaRepository.findByLahtoPvm(date));

        junapaivas = junapaivaPostProcessService.postProcess(new ArrayList<>(junapaivas));

        log.info(String.format("Retrieved %d trains in %s ms (date %s)", junapaivas.size(),
                Duration.between(now, ZonedDateTime.now()).toMillis(), date));

        return junapaivas;
    }

    private List<JunapaivaPrimaryKey> getChangedJunapaivasByVersion(final Long version, final long maxVersion) {
        Collection<Object[]> junapaivaPrimaryKeysAsObjectList = junapaivaRepository.findChangedJunapaivas(version, maxVersion,
                MAX_VERSION_QUERY_RETURNED_LIST_SIZE);

        List<JunapaivaPrimaryKey> junapaivaPrimaryKeysCasted = new ArrayList<JunapaivaPrimaryKey>(junapaivaPrimaryKeysAsObjectList.size());


        for (final Object[] primaryKey : junapaivaPrimaryKeysAsObjectList) {
            final Timestamp timestamp = (Timestamp) primaryKey[1];
            JunapaivaPrimaryKey junapaivaPrimaryKey = new JunapaivaPrimaryKey((String) primaryKey[0],timestamp.toLocalDateTime().toLocalDate());

            junapaivaPrimaryKeysCasted.add(junapaivaPrimaryKey);
        }

        return junapaivaPrimaryKeysCasted;
    }


}
