package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository;

import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AcceptanceDateRepository extends org.springframework.data.repository.Repository<Junapaiva, JunapaivaPrimaryKey> {
    @Query(value = "SELECT inner_jp.JUNANUMERO junanumero,slh_hh.MUOKKAUS_PVM hyvaksymisaika FROM  liike.junapaiva inner_jp, " +
            " aikataulu inner_ak, " +
            " aikataulujoukko inner_atj, " +
            " SAANNOLLISEN_LIIKENTEEN_HAKEMU slh, " +
            " slh_hakemushistoria slh_hh " +
            " WHERE " +
            " inner_jp.AIKT_ID = inner_ak.AIKT_ID " +
            " AND inner_ak.ATJ_ID = inner_atj.ATJ_ID " +
            " and inner_jp.junanumero in ?1" +
            " AND inner_jp.lahtopvm = ?2 " +
            " AND slh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            "    AND slh_hh.SLH_ID = slh.SLH_ID " +
            "   AND slh_hh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            " AND (slh.mua_id is null and slh.OPER_ID = inner_ak.OPER_ID AND slh.ATKAU_ID = inner_atj.ATKAU_ID)",nativeQuery = true)
    List<Object[]> findAtkauAcceptanceDates(List<String> junanumeroIds, final LocalDate localDate);

    @Query(value = "SELECT inner_jp.JUNANUMERO junanumero,slh_hh.MUOKKAUS_PVM hyvaksymisaika FROM  " +
            " junapaiva inner_jp, " +
            " aikataulu inner_ak, " +
            " aikataulujoukko inner_atj, " +
            " SAANNOLLISEN_LIIKENTEEN_HAKEMU slh, " +
            " slh_hakemushistoria slh_hh " +
            " WHERE " +
            " inner_jp.AIKT_ID = inner_ak.AIKT_ID " +
            " AND inner_ak.ATJ_ID = inner_atj.ATJ_ID " +
            " and inner_jp.junanumero in ?1" +
            " AND inner_jp.lahtopvm = ?2 " +
            " AND slh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            "    AND slh_hh.SLH_ID = slh.SLH_ID " +
            "   AND slh_hh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            " AND (slh.mua_id is not null and slh.MUA_ID = INNER_atj.MUA_ID)",nativeQuery = true)
    List<Object[]> findMuaAcceptanceDates(List<String> junanumeroIds, final LocalDate localDate);


    @Query(value = "  SELECT " +
            "  jp.junanumero junanumero,  hh.MUOKKAUS_PVM hyvaksymisaika " +
            "  FROM " +
            "   KIIREELLINEN_HAKEMUSHISTORIA hh, " +
            "   AIKATAULU ak, " +
            "   junapaiva jp " +
            "  WHERE " +
            "   jp  .AIKT_ID = ak  .AIKT_ID " +
            "   AND hh.KIHAK_ID = ak.KIHAK_ID " +
            "   AND jp.LAHTOPVM = ?2 " +
            "   AND jp.junanumero in ?1 " +
            "   AND hh.HAKEMUSTILA = 'Hyväksytty'",nativeQuery = true)
    List<Object[]> findKihakAcceptanceDates(List<String> junanumeroIds, final LocalDate localDate);













    @Query(value = "SELECT inner_ak.aikt_id,slh_hh.MUOKKAUS_PVM hyvaksymisaika FROM   " +
            " aikataulu inner_ak, " +
            " aikataulujoukko inner_atj, " +
            " SAANNOLLISEN_LIIKENTEEN_HAKEMU slh, " +
            " slh_hakemushistoria slh_hh " +
            " WHERE " +
            " inner_ak.ATJ_ID = inner_atj.ATJ_ID " +
            " and inner_ak.aikt_id in ?1" +
            " AND slh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            "    AND slh_hh.SLH_ID = slh.SLH_ID " +
            "   AND slh_hh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            " AND (slh.mua_id is null and slh.OPER_ID = inner_ak.OPER_ID AND slh.ATKAU_ID = inner_atj.ATKAU_ID)",nativeQuery = true)
    List<Object[]> findAtkauAcceptanceDates(List<Long> aikatauluIds);

    @Query(value = "SELECT inner_ak.aikt_id, slh_hh.MUOKKAUS_PVM hyvaksymisaika FROM  " +
            " aikataulu inner_ak, " +
            " aikataulujoukko inner_atj, " +
            " SAANNOLLISEN_LIIKENTEEN_HAKEMU slh, " +
            " slh_hakemushistoria slh_hh " +
            " WHERE " +
            " inner_ak.ATJ_ID = inner_atj.ATJ_ID " +
            " and inner_ak.aikt_id in ?1" +
            " AND slh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            "    AND slh_hh.SLH_ID = slh.SLH_ID " +
            "   AND slh_hh.HAKEMUSTILA = 'Jakopäätös julkaistu' " +
            " AND (slh.mua_id is not null and slh.MUA_ID = INNER_atj.MUA_ID)",nativeQuery = true)
    List<Object[]> findMuaAcceptanceDates(List<Long> aikatauluIds);


    @Query(value = "  SELECT " +
            "  ak.aikt_id, hh.MUOKKAUS_PVM hyvaksymisaika " +
            "  FROM " +
            "   KIIREELLINEN_HAKEMUSHISTORIA hh, " +
            "   AIKATAULU ak " +
            "  WHERE " +
            "   hh.KIHAK_ID = ak.KIHAK_ID " +
            "   AND ak.aikt_id in ?1" +
            "   AND hh.HAKEMUSTILA = 'Hyväksytty'",nativeQuery = true)
    List<Object[]> findKihakAcceptanceDates(List<Long> aikatauluIds);
}
