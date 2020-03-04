package fi.livi.rata.avoindata.updater.service.ruma;

import java.util.Locale;

public final class RumaUtils {

    private static final String INFRA_OID_PREFIX = "x.x.xxx.LIVI.INFRA.";
    private static final String INFRA_OID_PREFIX_NEW = "1.2.246.586.1.";

    private static final String JETI_OID_PREFIX = "x.x.xxx.LIVI.ETJ2.";
    private static final String JETI_OID_PREFIX_NEW = "1.2.246.586.2.";

    public static String normalizeTrakediaInfraOid(String oid) {
        if (oid == null) {
            return null;
        } else if (oid.startsWith(INFRA_OID_PREFIX_NEW)) {
            return oid;
        } else {
            return INFRA_OID_PREFIX_NEW + oid.substring(INFRA_OID_PREFIX.length());
        }
    }

    public static String normalizeJetiOid(String oid) {
        if (oid == null) {
            return null;
        } else if (oid.startsWith(JETI_OID_PREFIX_NEW)) {
            return oid;
        } else {
            return JETI_OID_PREFIX_NEW + oid.substring(JETI_OID_PREFIX.length());
        }
    }

    public static String ratakmvaliToString(
            final String ratanumero,
            final int alkuRatakm,
            final int alkuEtaisyys,
            final int loppuRatakm,
            final int loppuEtaisyys) {
        return String.format(Locale.ROOT, "(%s) %d+%04d > %d+%04d", ratanumero, alkuRatakm, alkuEtaisyys, loppuRatakm, loppuEtaisyys);
    }

}
