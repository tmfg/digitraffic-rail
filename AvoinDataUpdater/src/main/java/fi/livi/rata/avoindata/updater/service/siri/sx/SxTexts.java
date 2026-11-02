package fi.livi.rata.avoindata.updater.service.siri.sx;

/**
 * Text helpers for situation messages.
 */
final class SxTexts {

    /**
     * SIRI-SX profile limit for {@code Summary}.
     */
    static final int SUMMARY_MAX_LENGTH = 160;

    private static final String ELLIPSIS = "...";

    private SxTexts() {
    }

    /**
     * Returns the text as is when it fits in {@link #SUMMARY_MAX_LENGTH} characters. Otherwise returns the text cut
     * after the last whole word that fits, with {@code ...} added, so the result is at most
     * {@link #SUMMARY_MAX_LENGTH} characters long. A word is never cut. Characters are counted as code points,
     * so an emoji is never cut in half either.
     */
    static String summary(final String text) {
        final int[] chars = text.codePoints().toArray();
        if (chars.length <= SUMMARY_MAX_LENGTH) {
            return text;
        }
        final int end = findWordEnd(chars, SUMMARY_MAX_LENGTH - ELLIPSIS.length());
        return new String(chars, 0, end).stripTrailing() + ELLIPSIS;
    }

    /**
     * Returns the end of the last whole word that fits in {@code room} characters.
     */
    private static int findWordEnd(final int[] chars, final int room) {
        int end = room;
        if (Character.isWhitespace(chars[room])) {
            return end;
        }
        while (end > 0 && !Character.isWhitespace(chars[end - 1])) {
            end--;
        }
        return end;
    }
}
