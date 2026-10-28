package fi.livi.rata.avoindata.updater.service.siri.sx;

/** Text helpers for situation messages. */
final class SxTexts {

    /** SIRI-SX profile limit for {@code Summary}. */
    static final int SUMMARY_MAX_LENGTH = 160;

    private static final String ELLIPSIS = "...";

    private SxTexts() {
    }

    /**
     * Returns the text as is when it fits in {@link #SUMMARY_MAX_LENGTH}, otherwise the text cut after the last whole
     * word that fits, with {@code ...} added (the result is at most {@link #SUMMARY_MAX_LENGTH} long).
     */
    static String summary(final String text) {
        if (text.length() <= SUMMARY_MAX_LENGTH) {
            return text;
        }
        final int room = SUMMARY_MAX_LENGTH - ELLIPSIS.length();
        int end = room;
        if (!Character.isWhitespace(text.charAt(room))) {
            while (end > 0 && !Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            // A single word longer than the room is cut in the middle.
            if (end == 0) {
                end = room;
            }
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
