package fi.livi.rata.avoindata.LiikeInterface.kokoonpano.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Kokoonpano;

@Repository
public interface KokoonpanoRepository extends CrudRepository<Kokoonpano, Long> {
    @Query("select u from Kokoonpano u " +
            "inner join fetch u.aikataulu aikataulu " +
            "inner join fetch aikataulu.operaattori " +
            "inner join fetch aikataulu.aikataulunJunanumero " +
            "left join fetch aikataulu.lahiliikenteenLinjatunnus " +
            "left join fetch aikataulu.kiireellinenHakemus " +
            "inner join fetch aikataulu.junatyyppi junatyyppi " +
            "inner join fetch junatyyppi.junalaji junalaji " +
            "inner join fetch u.aikataulutapahtuma aikataulutapahtuma " +
            "inner join fetch u.viimeinenAikataulutapahtuma viimeinenAikataulutapahtuma " +
            "inner join fetch aikataulutapahtuma.liikennepaikka " +
            "left join fetch u.veturis veturis " +
            "left join fetch veturis.veturityyppi " +
            "left join fetch u.vaunus vaunus " +
            "left join fetch vaunus.kalustoyksikko " +
            "where u.lahtoPvm = ?1 " +
            "and EXISTS (" +
            "   select p.id from JupaTapahtuma p" +
            "   where p.kokoonpano = u and p.jupaTila = 'VOIMASSAOLEVA' and p.junapaiva.jupaTila = 'VOIMASSAOLEVA') " +
            "and junatyyppi.avoinData = 1 " +
            "and junalaji.avoinDataKokoonpanot = 1" +
            "and (aikataulu.kiireellinenHakemus.arkaluontoinen is null or aikataulu.kiireellinenHakemus.arkaluontoinen = 0)")
    List<Kokoonpano> findByLahtoPvm(LocalDate start);

    @Query("select u from Kokoonpano u " +
            "inner join fetch u.aikataulu aikataulu " +
            "inner join fetch aikataulu.operaattori " +
            "inner join fetch aikataulu.aikataulunJunanumero " +
            "left join fetch aikataulu.lahiliikenteenLinjatunnus " +
            "left join fetch aikataulu.kiireellinenHakemus " +
            "inner join fetch aikataulu.junatyyppi junatyyppi " +
            "inner join fetch junatyyppi.junalaji junalaji " +
            "inner join fetch u.aikataulutapahtuma aikataulutapahtuma " +
            "inner join fetch u.viimeinenAikataulutapahtuma viimeinenAikataulutapahtuma " +
            "inner join fetch aikataulutapahtuma.liikennepaikka " +
            "left join fetch u.veturis veturis " +
            "left join fetch veturis.veturityyppi " +
            "left join fetch u.vaunus vaunus " +
            "left join fetch vaunus.kalustoyksikko " +
            "where exists (" +
            "   select kp from Kokoonpano kp" +
            "   where kp.aikataulu = u.aikataulu" +
            "   and kp.lahtoPvm = u.lahtoPvm" +
            "   and kp.version > ?1) " +
            "and (" +
            "   exists (" +
            "   select p.jupaTila from JupaTapahtuma p" +
            "   where p.kokoonpano = u" +
            "       and p.jupaTila = 'VOIMASSAOLEVA' " +
            "       and p.junapaiva.jupaTila = 'VOIMASSAOLEVA'" +
            "    )" +
            "    or (" +
            "       not exists (select p.jupaTila from Junapaiva p where p.aikataulu = aikataulu)" +
            "    )" +
            ") " +
            "and u.lahtoPvm > ?2 " +
            "and junatyyppi.avoinData = 1 " +
            "and junalaji.avoinDataKokoonpanot = 1" +
            "and (aikataulu.kiireellinenHakemus.arkaluontoinen is null or aikataulu.kiireellinenHakemus.arkaluontoinen = 0)")
    List<Kokoonpano> findByVersion(Long version, LocalDate minDepartureDate);
}
