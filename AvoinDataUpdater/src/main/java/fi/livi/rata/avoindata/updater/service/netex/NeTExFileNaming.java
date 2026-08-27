package fi.livi.rata.avoindata.updater.service.netex;

import java.text.Normalizer;
import java.util.Locale;

import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExLine;

/**
 * Nordic-profile ZIP entry names. The profile only requires that common files
 * are prefixed with "_"; the rest follows the Norwegian reference layout,
 * trimmed of its repeated line id. The codespace prefix is kept because
 * consumers select group files by a leading three-letter codespace.
 */
public final class NeTExFileNaming {

    public static final String SHARED_DATA_XML = "_FTR_shared_data.xml";

    private static final String CODESPACE = "FTR";

    private NeTExFileNaming() {
    }

    public static String lineFileName(final NeTExLine line) {
        final String name = line.name() == null || line.name().isBlank()
                ? line.publicCode()
                : line.name();
        return "%s_%s_%s.xml".formatted(CODESPACE, sanitise(line.publicCode()), sanitise(name));
    }

    /**
     * Diacritics are stripped rather than emitted as UTF-8 entry names, matching
     * the Norwegian dataset (Flåm -> Flam, Skøyen -> Skoyen).
     */
    static String sanitise(final String value) {
        final String stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ø", "o")
                .replace("Ø", "O")
                .replace("æ", "ae")
                .replace("Æ", "Ae")
                .replace("ß", "ss");
        return stripped.replaceAll("[^A-Za-z0-9()]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    static String codespace() {
        return CODESPACE.toLowerCase(Locale.ROOT);
    }
}
