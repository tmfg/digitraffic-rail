package fi.livi.rata.avoindata.updater.service.siri.sx.model;

import java.util.Locale;

/**
 * A text in one language ({@code fi}, {@code sv} or {@code en}).
 */
public record SxText(String language, String text) {

    private static final String NEXT_STOP_PREFIX = "seuraava pysähdys";

    /**
     * True for a Finnish text that only tells the next stop of the train, for example "Seuraava pysähdys: Tampere".
     */
    public boolean isNextStop() {
        return "fi".equals(language) && text.toLowerCase(Locale.ROOT).startsWith(NEXT_STOP_PREFIX);
    }
}
