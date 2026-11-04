package fi.livi.rata.avoindata.updater.service.netex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;
import fi.livi.rata.avoindata.updater.service.netex.PublishedJourneyDraft.PublishedTrack;

/**
 * Measures how long {@link NeTExPublishedJourneyWriter#persistWindow} takes and how much heap it needs for a
 * production sized window. Not part of normal builds, run it by hand against the local database:
 *
 * <pre>
 * mvn -pl AvoinDataUpdater test -Dtest=NeTExPublishedJourneyWriterLoadTest -Dnetex.load.enabled=true \
 *     -Dnetex.load.lookahead-days=14 -Dnetex.load.trains-per-day=1260 -Dnetex.load.tracks-per-train=13 \
 *     -DargLine="-Xmx512m"
 * </pre>
 *
 * The result is logged as {@code event=netex.load.persist_window}. The test runs in a transaction that is rolled
 * back, like the production persist runs inside the package transaction.
 */
@Transactional
@EnabledIfSystemProperty(named = "netex.load.enabled", matches = "true")
public class NeTExPublishedJourneyWriterLoadTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(NeTExPublishedJourneyWriterLoadTest.class);

    private static final int LOOKBACK_DAYS = 2;
    private static final long SAMPLE_INTERVAL_MS = 20;

    @Autowired
    private NeTExPublishedJourneyRepository journeyRepository;

    @Autowired
    private NeTExPublishedJourneyBatchInserter batchInserter;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void persistWindowForConfiguredHorizon() throws InterruptedException {
        final int lookaheadDays = Integer.getInteger("netex.load.lookahead-days", 14);
        final int trainsPerDay = Integer.getInteger("netex.load.trains-per-day", 1260);
        final int tracksPerTrain = Integer.getInteger("netex.load.tracks-per-train", 13);

        final LocalDate today = DateProvider.dateInHelsinki();
        final List<PublishedJourneyDraft> drafts = drafts(today, lookaheadDays, trainsPerDay, tracksPerTrain);
        final NeTExDataset dataset = new NeTExDataset(List.of(), drafts,
                PublishedJourneyWindow.of(today, LOOKBACK_DAYS, lookaheadDays), null, null, List.of(), List.of(), List.of(),
                null, List.of());
        final NeTExPublishedJourneyWriter writer =
                new NeTExPublishedJourneyWriter(journeyRepository, batchInserter, true);

        System.gc();
        final long heapBefore = usedHeap();
        final PeakHeapSampler sampler = new PeakHeapSampler();
        sampler.start();

        final long start = System.nanoTime();
        writer.persistWindow(dataset);
        journeyRepository.flush();
        final long durationMs = (System.nanoTime() - start) / 1_000_000;

        sampler.stopAndJoin();
        // The persistence context still holds the entities here, so this is what the transaction keeps alive.
        System.gc();
        final long heapRetained = usedHeap();

        final long expectedTracks = (long) drafts.size() * tracksPerTrain;
        log.info("event=netex.load.persist_window lookahead_days={} lookback_days={} trains_per_day={} "
                        + "tracks_per_train={} journeys={} tracks={} duration_ms={} heap_before_mb={} "
                        + "heap_peak_mb={} heap_retained_mb={} max_heap_mb={}",
                lookaheadDays, LOOKBACK_DAYS, trainsPerDay, tracksPerTrain, drafts.size(), expectedTracks,
                durationMs, mb(heapBefore), mb(sampler.peak.get()), mb(heapRetained),
                mb(Runtime.getRuntime().maxMemory()));

        entityManager.clear();
        assertThat(journeyRepository.count()).isGreaterThanOrEqualTo(drafts.size());
    }

    private static List<PublishedJourneyDraft> drafts(final LocalDate today, final int lookaheadDays,
                                                     final int trainsPerDay, final int tracksPerTrain) {
        final List<PublishedJourneyDraft> drafts = new ArrayList<>();
        for (LocalDate date = today.minusDays(LOOKBACK_DAYS); !date.isAfter(today.plusDays(lookaheadDays));
             date = date.plusDays(1)) {
            for (int train = 1; train <= trainsPerDay; train++) {
                drafts.add(new PublishedJourneyDraft(new TrainId(train, date),
                        "FTR:ServiceJourney:" + train + "-load-" + date,
                        "FTR:Line:" + (train % 40),
                        "FTR:Operator:vr",
                        "FTR:JourneyPattern:" + train,
                        tracks(tracksPerTrain)));
            }
        }
        return drafts;
    }

    private static List<PublishedTrack> tracks(final int count) {
        final List<PublishedTrack> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tracks.add(new PublishedTrack("S" + i, String.valueOf(1 + i % 9), 0, i));
        }
        return tracks;
    }

    private static long usedHeap() {
        final Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long mb(final long bytes) {
        return bytes / (1024 * 1024);
    }

    /** Samples used heap, so the peak includes garbage that has not been collected yet. */
    private static final class PeakHeapSampler extends Thread {
        private final AtomicLong peak = new AtomicLong();
        private volatile boolean running = true;

        PeakHeapSampler() {
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                peak.accumulateAndGet(usedHeap(), Math::max);
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS);
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }

        void stopAndJoin() throws InterruptedException {
            running = false;
            join();
        }
    }
}
