package fi.livi.rata.avoindata.updater.service.siri.sx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SxTextsTest {

    @Test
    public void textThatFitsIsNotChanged() {
        final String text = "a".repeat(160);

        assertEquals(text, SxTexts.summary(text));
    }

    @Test
    public void longTextIsCutAfterLastWholeWord() {
        final String text = "sana ".repeat(50).trim();

        final String summary = SxTexts.summary(text);

        assertEquals(157, summary.length());
        assertTrue(summary.endsWith("sana..."));
    }

    @Test
    public void cutDoesNotLeaveTrailingSpaceBeforeDots() {
        // The 157th character is a space, so the cut is exactly there.
        final String text = "a".repeat(156) + " bbbbbbbbbb";

        assertEquals("a".repeat(156) + "...", SxTexts.summary(text));
    }

    @Test
    public void wordIsNeverCut() {
        assertEquals("aaa...", SxTexts.summary("aaa " + "x".repeat(300)));
    }

    @Test
    public void summaryIsNeverOverLimit() {
        for (int length = 150; length < 200; length++) {
            final String text = "ab ".repeat(length).substring(0, length);
            assertTrue(SxTexts.summary(text).length() <= 160, "length " + length);
        }
    }

    @Test
    public void characterOutsideBasicPlaneIsNotCutInHalf() {
        final String summary = SxTexts.summary("😀 ".repeat(100));

        assertEquals(160, summary.codePointCount(0, summary.length()));
        assertTrue(summary.endsWith("😀..."));
    }
}
