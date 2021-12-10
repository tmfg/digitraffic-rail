package fi.livi.rata.avoindata.LiikeInterface.purkaja;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulu;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Poikkeuspaiva;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.AcceptanceDateRepository;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.entity.Peruminen;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.repository.AikatauluRepository;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.repository.PeruminenRepository;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.repository.PoikkeuspaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.util.BatchExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AikatauluController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AikatauluRepository aikatauluRepository;
    @Autowired
    private PeruminenRepository peruminenRepository;
    @Autowired
    private PoikkeuspaivaRepository poikkeuspaivaRepository;
    @Autowired
    private AcceptanceDateRepository acceptanceDateRepository;
    @Autowired
    private BatchExecutionService bes;

    @RequestMapping("/avoin/regular-schedule-ids")
    @ResponseBody
    @JsonView(AikatauluController.class)
    public synchronized List<Long> getRegularSchedulesFromDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final List<Long> aikatauluList = aikatauluRepository.findRegularSchedulesAfterDate(date);

        return aikatauluList;
    }


    @RequestMapping("/avoin/adhoc-schedule-ids")
    @ResponseBody
    @JsonView(AikatauluController.class)
    public List<Long> getAdhocSchedulesFromDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final List<Long> aikatauluList = aikatauluRepository.findAdhocSchedulesAfterDate(date);

        return aikatauluList;
    }

    @RequestMapping("/avoin/schedules")
    @ResponseBody
    @JsonView(AikatauluController.class)
    public Iterable<Aikataulu> findById(@RequestParam  List<Long> ids) {
        final Iterable<Aikataulu> aikatauluList = aikatauluRepository.findAll(ids);

        injectTransientValues(Lists.newArrayList(aikatauluList));

        return aikatauluList;
    }

    private void fillTimetableAcceptanceDates(final List<Aikataulu> aikataulus) {
        final List<Long> aikatauluIds = Lists.newArrayList(Lists.transform(aikataulus, s -> s.getId()));

        final Map<Long, ZonedDateTime> muaDates = objectToAcceptanceDateMap(
                bes.execute(aikatauluIds, s -> acceptanceDateRepository.findMuaAcceptanceDates(s)));
        aikatauluIds.removeAll(muaDates.keySet());

        final Map<Long, ZonedDateTime> kihakDates = objectToAcceptanceDateMap(
                bes.execute(aikatauluIds, s -> acceptanceDateRepository.findKihakAcceptanceDates(s)));
        aikatauluIds.removeAll(kihakDates.keySet());

        final Map<Long, ZonedDateTime> atkauDates = objectToAcceptanceDateMap(
                bes.execute(aikatauluIds, s -> acceptanceDateRepository.findAtkauAcceptanceDates(s)));
        aikatauluIds.removeAll(atkauDates.keySet());

        for (final Aikataulu aikataulu : aikataulus) {
            final ZonedDateTime muaAcceptanceDate = muaDates.get(aikataulu.getId());
            final ZonedDateTime kihakAcceptanceDate = kihakDates.get(aikataulu.getId());
            final ZonedDateTime atkauAcceptanceDate = atkauDates.get(aikataulu.getId());

            if (muaAcceptanceDate != null) {
                aikataulu.hyvaksymisaika = muaAcceptanceDate;
            } else if (kihakAcceptanceDate != null) {
                aikataulu.hyvaksymisaika = kihakAcceptanceDate;
            } else if (atkauAcceptanceDate != null) {
                aikataulu.hyvaksymisaika = atkauAcceptanceDate;
            } else {
                log.error("Acceptance date not found for {}", aikataulu);
            }
        }
    }

    private Map<Long, ZonedDateTime> objectToAcceptanceDateMap(final List<Object[]> muaObjects) {
        final Map<Long, ZonedDateTime> output = new HashMap<>(muaObjects.size());
        for (final Object[] muaObject : muaObjects) {
            final ZonedDateTime acceptanceDate = ZonedDateTime.ofInstant(((Timestamp) muaObject[1]).toInstant(),
                    ZoneId.of("Europe/Helsinki"));
            output.put(((BigDecimal) muaObject[0]).longValue(), acceptanceDate);
        }

        return output;
    }

    private void injectTransientValues(final List<Aikataulu> aikatauluList) {
        final ImmutableMap<Long, Aikataulu> aikatauluMap = Maps.uniqueIndex(aikatauluList, s -> s.getId());

        final List<Peruminen> peruminens = bes.execute(aikatauluList, s -> peruminenRepository.findByAikatauluIn(s));
        for (final Peruminen peruminen : peruminens) {
            final Aikataulu aikataulu = aikatauluMap.get(peruminen.aikataulu.getId());
            aikataulu.peruminens.add(peruminen);
        }

        final List<Poikkeuspaiva> poikkeuspaivas = bes.execute(aikatauluList, s -> poikkeuspaivaRepository.findByAikatauluIn(s));
        for (final Poikkeuspaiva poikkeuspaiva : poikkeuspaivas) {
            final Aikataulu aikataulu = aikatauluMap.get(poikkeuspaiva.aikataulu.getId());
            aikataulu.poikkeuspaivas.add(poikkeuspaiva);
        }

        fillTimetableAcceptanceDates(aikatauluList);
    }
}
