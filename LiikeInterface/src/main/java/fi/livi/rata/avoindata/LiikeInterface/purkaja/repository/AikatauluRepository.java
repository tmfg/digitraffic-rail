package fi.livi.rata.avoindata.LiikeInterface.purkaja.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulu;

@Repository
public interface AikatauluRepository extends CrudRepository<Aikataulu, Long> {

    String WHERE_LPTYP = " and atr.liikennepaikka.lptypId IN (1,2,3)";
    String WHERE_DATE_AFTER = " and (a.alkupvm >= ?1 or ?1 between a.alkupvm and a.loppupvm) ";
    String AIKATAULU_JOINS = " inner join fetch a.aikataulunJunanumero a_jn" +
            " inner join fetch a.aikataulurivis atr" +
            " inner join fetch a.operaattori op" +
            " inner join fetch a.junatyyppi junatyyppi" +
            " inner join  a.aikataulujoukko atj " +
            " left join fetch a.lahiliikenteenLinjatunnus lahiliikenteenLinjatunnus" +
            " left join fetch a.kiireellinenHakemus kh " +
            " left join fetch atr.liikennepaikanRaide " +
            " left join fetch atr.liikennepaikka " +
            " left join fetch atr.lahto " +
            " left join fetch atr.saapuminen ";

    String AIKATAULU_ID_JOINS = " inner join a.aikataulunJunanumero a_jn" +
            " inner join a.aikataulurivis atr" +
            " inner join a.aikataulujoukko atj " +
            " left join a.kiireellinenHakemus kh " +
            " left join atr.liikennepaikka " +
            " left join atj.aikataulukausi ";

    @Query("select distinct a.id from Aikataulu a " +
            AIKATAULU_ID_JOINS +
            " where " +
            "   (" +
            "       atj.atjType = 'JAKOP' and atj.julkaisuhetki is not null and " +
            "       a.kiireellinenHakemus is null and" +
            "       a.aikataulupaatos in ('Hyväksytty','Hyväksytty muutoksin') and" +
            "       (atj.aikataulukausi.voimassaAlkuPvm >= ?1 or " +
            "       ?1 between atj.aikataulukausi.voimassaAlkuPvm and atj.aikataulukausi.voimassaLoppuPvm)" +
            "   )" + WHERE_LPTYP + " Order by a.id")
    List<Long> findRegularSchedulesAfterDate(LocalDate startDate);

    @Query("select distinct a.id from Aikataulu a " +
            AIKATAULU_ID_JOINS +
            " where " +
            "   (" +
            "       atj.atjType = 'KIIRE' and" +
            "       a.kiireellinenHakemus is not null and" +
            "       a.kaiktType = 'K' and " +
            "       kh.hakemustila = 'Hyväksytty' and" +
            "       (kh.arkaluontoinen = 0 or kh.arkaluontoinen is null)" +
            "   )" +
            WHERE_DATE_AFTER + WHERE_LPTYP + " Order by a.id")
    List<Long> findAdhocSchedulesAfterDate(LocalDate startDate);

    @Query("select distinct a from Aikataulu a " +
            AIKATAULU_JOINS +
            "   where a.id in ?1" + WHERE_LPTYP)
    List<Aikataulu> findAll(List<Long> ids);
}
