package fi.livi.rata.avoindata.updater.service.netex;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * Inserts the published journeys and their planned tracks with JDBC batches.
 *
 * <p>
 * JPA cannot batch these inserts because the tables use auto-increment ids, so it
 * would send one INSERT per row. Here the journeys are inserted first, their
 * generated ids are read back by the unique (version, date, train) key, and then
 * the tracks are inserted using those ids.
 *
 * <p>
 * Uses the same connection as the surrounding transaction, so the rows are
 * committed or rolled back together with the NeTEx package.
 */
@Component
public class NeTExPublishedJourneyBatchInserter {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_JOURNEY = """
            INSERT INTO netex_published_journey
                (train_number, departure_date, service_journey_id, line_id, operator_ref, journey_pattern_ref,
                 dataset_version, generated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_JOURNEY_IDS = """
            SELECT id, train_number, departure_date
            FROM netex_published_journey
            WHERE dataset_version = ?
            """;

    private static final String INSERT_TRACK = """
            INSERT INTO netex_published_journey_track
                (journey_id, station_short_code, planned_track, visit_index, sequence_index)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public NeTExPublishedJourneyBatchInserter(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts the drafts as one new dataset version.
     */
    public void insert(final List<PublishedJourneyDraft> drafts, final long datasetVersion,
            final Instant generatedAt) {
        if (drafts.isEmpty()) {
            return;
        }
        final Timestamp generated = Timestamp.from(generatedAt);

        jdbcTemplate.batchUpdate(INSERT_JOURNEY, drafts, BATCH_SIZE, (ps, draft) -> {
            ps.setLong(1, draft.trainId().trainNumber);
            ps.setObject(2, draft.trainId().departureDate);
            ps.setString(3, draft.serviceJourneyId());
            ps.setString(4, draft.lineRef());
            ps.setString(5, draft.operatorRef());
            ps.setString(6, draft.journeyPatternRef());
            ps.setLong(7, datasetVersion);
            ps.setTimestamp(8, generated);
        });

        final Map<TrainId, Long> journeyIds = new HashMap<>();
        jdbcTemplate.query(SELECT_JOURNEY_IDS, rs -> {
            journeyIds.put(new TrainId(rs.getLong("train_number"), rs.getObject("departure_date", LocalDate.class)),
                    rs.getLong("id"));
        }, datasetVersion);

        final List<TrackRow> trackRows = new ArrayList<>();
        for (final PublishedJourneyDraft draft : drafts) {
            final Long journeyId = journeyIds.get(draft.trainId());
            for (final PublishedJourneyDraft.PublishedTrack track : draft.tracks()) {
                trackRows.add(new TrackRow(journeyId, track));
            }
        }

        jdbcTemplate.batchUpdate(INSERT_TRACK, trackRows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.journeyId());
            ps.setString(2, row.track().stationShortCode());
            ps.setString(3, row.track().plannedTrack());
            ps.setInt(4, row.track().visitIndex());
            ps.setInt(5, row.track().sequenceIndex());
        });
    }

    private record TrackRow(long journeyId, PublishedJourneyDraft.PublishedTrack track) {
    }
}
