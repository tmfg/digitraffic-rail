package fi.livi.rata.avoindata.updater.service.netex;

import java.util.Arrays;
import java.util.List;

/**
 * Organisations responsible for the passenger services in the dataset.
 * Each one has its own Network and GroupOfLines; Operator is VR for all of them.
 * The main authority has the main Network, the others go to additionalNetworks.
 */
public enum NeTExAuthority {
    VR("VR", "VR", "VR-Yhtymä Oyj", "https://www.vr.fi/en", 10),
    HSL("HSL", "HSL", "Helsingin seudun liikenne -kuntayhtymä", "https://www.hsl.fi/en", 9995);

    private final String code;
    private final String displayName;
    private final String legalName;
    private final String url;
    private final int companyNumber;

    NeTExAuthority(final String code, final String displayName, final String legalName, final String url,
            final int companyNumber) {
        this.code = code;
        this.displayName = displayName;
        this.legalName = legalName;
        this.url = url;
        this.companyNumber = companyNumber;
    }

    public static NeTExAuthority mainAuthority() {
        return VR;
    }

    public static List<NeTExAuthority> additionalAuthorities() {
        return Arrays.stream(values())
                .filter(authority -> authority != mainAuthority())
                .toList();
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String legalName() {
        return legalName;
    }

    public String url() {
        return url;
    }

    public int companyNumber() {
        return companyNumber;
    }
}
