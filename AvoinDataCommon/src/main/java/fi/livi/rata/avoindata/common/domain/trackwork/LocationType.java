package fi.livi.rata.avoindata.common.domain.trackwork;

public enum LocationType {
    WORK,
    FIREWORK,
    SPEED_LIMIT;

    public static LocationType fromKohdeType(final String kohdeType) {
        switch (kohdeType) {
            case "TYONKOHDE" -> {
                return WORK;
            }
            case "TULITYO" -> {
                return FIREWORK;
            }
            case "NOPEUSRAJOITUS" -> {
                return SPEED_LIMIT;
            }
        }
        throw new IllegalArgumentException("Unknown KohdeType: " + kohdeType);
    }
}
