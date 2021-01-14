package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma;

import static fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController.TRAINS_TO_FETCH_PER_QUERY;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.AcceptanceDate;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.JupaTapahtuma;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Syyluokka;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Syytieto;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.AcceptanceDateRepository;

@Service
public class JunapaivaPostProcessService {
    private Logger log = LoggerFactory.getLogger(JunapaivaPostProcessService.class);

    @Value("#{'${liikeinterface.accepted-syyluokka}'.split(',')}")
    private Set<String> acceptableSyyluokkas;

    @Value("#{'${liikeinterface.accepted-syykoodi}'.split(',')}")
    private Set<String> acceptableSyykoodi;

    @Value("#{'${liikeinterface.accepted-tarkentava-syykoodi}'.split(',')}")
    private Set<String> acceptableTarkentavaSyykoodi;

    @Autowired
    private AcceptanceDateRepository acceptanceDateRepository;

    public List<Junapaiva> postProcess(List<Junapaiva> junapaivaList) {
        removeDeletedSyytietosFromJunapaivas(junapaivaList);

        removeSecretSyytietosFromJunapaivas(junapaivaList);

        fillTimetableAcceptanceDates(junapaivaList);

        return junapaivaList;
    }

    private void removeDeletedSyytietosFromJunapaivas(final Collection<Junapaiva> junapaivas) {

        for (final Junapaiva junapaiva : junapaivas) {
            for (final JupaTapahtuma jupaTapahtuma : junapaiva.jupaTapahtumas) {
                List<Syytieto> syyTietosToBeDeleted = new ArrayList<>(0);
                for (final Syytieto syytieto : jupaTapahtuma.syytietos) {
                    if (syytieto.poistettu == 1) {
                        syyTietosToBeDeleted.add(syytieto);
                    }
                }
                jupaTapahtuma.syytietos.removeAll(syyTietosToBeDeleted);
            }
        }
    }

    private void removeSecretSyytietosFromJunapaivas(final Collection<Junapaiva> junapaivas) {
        for (final Junapaiva junapaiva : junapaivas) {
            for (final JupaTapahtuma jupaTapahtuma : junapaiva.jupaTapahtumas) {
                List<Syytieto> syyTietosToBeDeleted = new ArrayList<>(0);

                for (final Syytieto syytieto : jupaTapahtuma.syytietos) {
                    Syyluokka syyluokka = syytieto.syykoodi.syyluokka;
                    if (syyluokka != null && !acceptableSyyluokkas.contains(syyluokka.tunnus)) {
                        syytieto.syykoodi = null;
                        syytieto.tarkentavaSyykoodi = null;
                    } else if (syytieto.syykoodi != null && !acceptableSyykoodi.contains(syytieto.syykoodi.syykoodi)) {
                        syytieto.syykoodi = null;
                        syytieto.tarkentavaSyykoodi = null;
                    } else if (syytieto.tarkentavaSyykoodi != null && !acceptableTarkentavaSyykoodi.contains(
                            syytieto.tarkentavaSyykoodi.tark_syykoodi)) {
                        syytieto.tarkentavaSyykoodi = null;
                    }
                }
                jupaTapahtuma.syytietos.removeAll(syyTietosToBeDeleted);
            }
        }
    }

    private void fillTimetableAcceptanceDates(final Collection<Junapaiva> junapaivas) {
        MultiValueMap<LocalDate, Junapaiva> junapaivaIds = new LinkedMultiValueMap<LocalDate, Junapaiva>();
        for (final Junapaiva junapaiva : junapaivas) {
            junapaivaIds.add(junapaiva.id.lahtopvm, junapaiva);
        }

        for (final LocalDate localDate : junapaivaIds.keySet()) {
            final List<Junapaiva> junapaivaList = junapaivaIds.get(localDate);

            Map<String, AcceptanceDate> acceptanceDates = new HashMap<>();

            List<String> junanumeroList = new ArrayList<>();
            for (final Junapaiva junapaiva : junapaivaList) {
                junanumeroList.add(junapaiva.id.junanumero);
            }

            acceptanceDates.putAll(getAcceptanceDates(junanumeroList,
                    junanumeros -> acceptanceDateRepository.findMuaAcceptanceDates(junanumeros, localDate)));
            acceptanceDates.putAll(getAcceptanceDates(junanumeroList,
                    junanumeros -> acceptanceDateRepository.findKihakAcceptanceDates(junanumeros, localDate)));
            acceptanceDates.putAll(getAcceptanceDates(junanumeroList,
                    junanumeros -> acceptanceDateRepository.findAtkauAcceptanceDates(junanumeros, localDate)));

            if (!junanumeroList.isEmpty()) {
                log.error("Left {}", junapaivaList.size());
            }

            for (final Junapaiva junapaiva : junapaivaList) {
                final AcceptanceDate acceptanceDate = acceptanceDates.get(junapaiva.id.junanumero);
                if (acceptanceDate == null) {
                    log.error("junanumeroList");
                }
                junapaiva.aikataulu.hyvaksymisaika = acceptanceDate.hyvaksymisaika;
            }
        }
    }

    private Map<String, AcceptanceDate> getAcceptanceDates(List<String> junanumeros,
            Function<List<String>, List<Object[]>> acceptanceDateSupplier) {
        if (junanumeros.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, AcceptanceDate> acceptanceDates = new HashMap<>(junanumeros.size());
        List<String> handledJunanumeros = new ArrayList<>(junanumeros.size());

        for (int i = 0; i < junanumeros.size(); i += TRAINS_TO_FETCH_PER_QUERY) {
            final List<String> junanumeroSublist = junanumeros.subList(i, Math.min(junanumeros.size(), i + TRAINS_TO_FETCH_PER_QUERY));
            final List<Object[]> acceptanceDatesAsArray = acceptanceDateSupplier.apply(junanumeroSublist);
            for (final Object[] acceptanceDateArray : acceptanceDatesAsArray) {
                AcceptanceDate acceptanceDate = new AcceptanceDate();
                acceptanceDate.junanumero = acceptanceDateArray[0].toString();
                acceptanceDate.hyvaksymisaika = ZonedDateTime.ofInstant(((Timestamp) acceptanceDateArray[1]).toInstant(),
                        ZoneId.of("Europe/Helsinki"));
                acceptanceDates.put(acceptanceDate.junanumero, acceptanceDate);

                handledJunanumeros.add(acceptanceDate.junanumero);
            }
        }

        junanumeros.removeAll(handledJunanumeros);

        return acceptanceDates;
    }
}
