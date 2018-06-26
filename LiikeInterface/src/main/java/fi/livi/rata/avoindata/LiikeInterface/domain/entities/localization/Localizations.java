package fi.livi.rata.avoindata.LiikeInterface.domain.entities.localization;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junalaji;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junatyyppi;

import java.util.List;

public class Localizations {
    public final List<Junalaji> junalajis;
    public final List<Junatyyppi> junatyyppis;
    public final List<Vetovoimalaji> vetovoimalajis;

    public Localizations(final List<Junalaji> junalajis, final List<Junatyyppi> junatyyppis, final List<Vetovoimalaji> vetovoimalajis) {
        this.junalajis = junalajis;
        this.junatyyppis = junatyyppis;
        this.vetovoimalajis = vetovoimalajis;
    }
}
