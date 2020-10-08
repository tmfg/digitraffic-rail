package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;

@Repository
public interface JunapaivaRepository extends CrudRepository<Junapaiva, JunapaivaPrimaryKey> {
    @Query("select distinct junapaiva from Junapaiva junapaiva " +
            "inner join fetch junapaiva.aikataulu aikataulu " +
            "inner join fetch aikataulu.operaattori " +
            "inner join fetch aikataulu.aikataulunJunanumero " +
            "left join fetch aikataulu.kiireellinenHakemus " +
            "left join fetch aikataulu.lahiliikenteenLinjatunnus " +
            "inner join fetch aikataulu.junatyyppi junatyyppi " +
            "inner join fetch junapaiva.jupaTapahtumas junaTapahtumas " +
            "inner join fetch junaTapahtumas.liikennepaikka liikennepaikka " +
            "left join fetch junaTapahtumas.liikennepaikanRaide " +
            "left join fetch junaTapahtumas.syytietos syytietos " +
            "left join fetch syytietos.syyluokka syyluokka " +
            "left join fetch syytietos.syykoodi syykoodi " +
            "where junapaiva.id.lahtopvm = ?1 " +
            "and junatyyppi.avoinData = 1 " +
            "and liikennepaikka.lptypId IN (1,2,3)" +
            "and (aikataulu.kiireellinenHakemus.arkaluontoinen is null or aikataulu.kiireellinenHakemus.arkaluontoinen = 0)")
    List<Junapaiva> findByLahtoPvm(LocalDate start);

    @Query("select jt.id.junanumero,jt.id.lahtopvm, max(jt.version) " +
            "from JupaTapahtuma jt " +
            "where jt.id.lahtopvm = ?1 " +
            "and jt.id.junanumero in ?2 " +
            "group by jt.id.junanumero,jt.id.lahtopvm")
    List<Object[]> findMaxVersions(LocalDate start, Collection<String> junanumeros);

    @Query(value = "with tapahtumat as" +
            " (SELECT jupat.junanumero, jupat.lahtopvm, ora_rowscn" +
            "    FROM jupa_tapahtuma jupat" +
            "   WHERE jupat.ora_rowscn > ?1 and jupat.ora_rowscn <= ?2" +
            "     and lahtopvm > trunc(sysdate - 2))," +
            "syyt as" +
            " (SELECT syyt.junanumero, syyt.lahtopvm, ora_rowscn" +
            "    FROM syytieto syyt" +
            "   WHERE syyt.ora_rowscn > ?1 and syyt.ora_rowscn <= ?2" +
            "     and lahtopvm > trunc(sysdate - 2))," +
            "uusin_muutos as" +
            " (select max(ora_rowscn) orascn" +
            "    from (select max(ora_rowscn) ora_rowscn" +
            "            from syyt" +
            "          UNION" +
            "          SELECT max(ora_rowscn) ora_rowscn" +
            "            from tapahtumat))" +
            "select sub.*, (select orascn from uusin_muutos) uusin" +
            "  from (select junanumero, lahtopvm" +
            "          from syyt" +
            "        UNION" +
            "        select junanumero, lahtopvm" +
            "          from tapahtumat) sub" +
            " where rownum <= ?3", nativeQuery = true)
    List<Object[]> findChangedJunapaivas(Long version, final long maxVersion, int maxCount);

    @Query("select  junapaiva from Junapaiva junapaiva " +
            "inner join fetch junapaiva.aikataulu aikataulu " +
            "inner join fetch aikataulu.operaattori " +
            "inner join fetch aikataulu.aikataulunJunanumero " +
            "left join fetch aikataulu.kiireellinenHakemus " +
            "left join fetch aikataulu.lahiliikenteenLinjatunnus " +
            "inner join fetch aikataulu.junatyyppi junatyyppi " +
            "inner join fetch junapaiva.jupaTapahtumas junaTapahtumas " +
            "inner join fetch junaTapahtumas.liikennepaikka liikennepaikka " +
            "inner join fetch junaTapahtumas.aikataulutapahtuma aikataulutapahtuma " +
            "inner join fetch aikataulutapahtuma.aikataulurivi aikataulurivi " +
            "left join fetch aikataulurivi.raidemuutos raidemuutos " +
            "left join fetch junaTapahtumas.liikennepaikanRaide " +
            "left join fetch junaTapahtumas.syytietos syytietos " +
            "left join fetch syytietos.syyluokka syyluokka " +
            "left join fetch syytietos.syykoodi syykoodi " +
            "where junapaiva.id in ?1 " +
            "and junatyyppi.avoinData = 1 " +
            "and liikennepaikka.lptypId IN (1,2,3)" +
            "and (aikataulu.kiireellinenHakemus.arkaluontoinen is null or aikataulu.kiireellinenHakemus.arkaluontoinen = 0)")
    List<Junapaiva> findByPrimaryKeys(Collection<JunapaivaPrimaryKey> junapaivaPrimaryKeys);

    @Query("select junapaiva from Junapaiva junapaiva " +
            "inner join fetch junapaiva.aikataulu aikataulu " +
            "left join fetch aikataulu.kiireellinenHakemus " +
            "where junapaiva.id.lahtopvm = ?1 and aikataulu.kiireellinenHakemus.arkaluontoinen = 1")
    List<Junapaiva> findClassifiedTrains(LocalDate departureDate);

    @Query("select junapaiva.id from Junapaiva junapaiva " +
            "inner join junapaiva.aikataulu aikataulu " +
            "left join aikataulu.kiireellinenHakemus " +
            "where junapaiva.id in ?1 and aikataulu.kiireellinenHakemus.arkaluontoinen = 1")
    Set<JunapaivaPrimaryKey> findClassifiedTrains(Iterable<JunapaivaPrimaryKey> keys);
}
