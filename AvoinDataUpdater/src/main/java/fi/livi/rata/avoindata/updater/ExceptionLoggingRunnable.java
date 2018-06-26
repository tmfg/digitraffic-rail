package fi.livi.rata.avoindata.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler nielee kaikki poikkeukset. Jopa OutOfMemoryError jää schedulerin uhriksi joten tämä luokka ottaa kaikki virheet kiinni ja
 * kirjoittaa ne lokiin. RuntimeException tasoiset ainoastaan lokitetaan, mutta muiden tapauksessa lokirivin lisäksi heitetään myös
 * poikkeus, jotta tämä jobi saadaan pois schedulerilta, koska ei ole mitään hyötyä yrittää ajaa uudestaan jos esimerkiksi muisti
 * loppunut kesken.
 *
 * @author mattioi
 *         From Liike-Project
 */
public class ExceptionLoggingRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingRunnable.class);

    private final Runnable runnable;

    public ExceptionLoggingRunnable(final Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        try {
            runnable.run();
        } catch (final RuntimeException e) {
            log.error("Runnablen suorituksessa tapahtui poikkeus.", e);
        } catch (final Throwable t) { // scheduler nielee jopa nämä
            log.error(
                    "Runnablen suorituksessa tapahtui virhe. Jos runnable oli schedulerilla ajossa niin se on poistettu jonosta pysyvästi.",
                    t);
            // Poistetaan jobi schedulerin jonosta. Ei ole enään järkeä yrittää jos tulee OOM tms. virhe.
            // Vähäpätöisemmät virheet jää jo RuntimeException haaraan.
            //noinspection ProhibitedExceptionThrown
            throw new RuntimeException("Tämä poikkeus poistaa jobi schedulerin jonosta", t);
        }
    }
}