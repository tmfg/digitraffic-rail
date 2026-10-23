package fi.livi.rata.avoindata.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

class GeneratedExportRepositoryTest extends BaseTest {

    @Autowired
    private GeneratedExportRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findLatestCreatedByFileNameReturnsLatestExportWhenExistsReturnsLatestTimestamp() {
        final ZonedDateTime created1 = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        final ZonedDateTime created2 = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);

        saveExport("gtfs-passenger-stops.zip", created1);
        saveExport("gtfs-passenger-stops.zip", created2);

        final Optional<Instant> result = repository.findLatestCreatedByFileName("gtfs-passenger-stops.zip");

        assertTrue(result.isPresent());
        assertEquals(created2.toInstant(), result.get());
    }

    @Test
    void findLatestCreatedByFileNameReturnsEmptyWhenFileNotFound() {
        final Optional<Instant> result = repository.findLatestCreatedByFileName("gtfs-passenger-stops.zip");

        assertFalse(result.isPresent());
    }

    @Test
    void findLatestCreatedByFileNameIgnoresOtherFilenames() {
        final ZonedDateTime created1 = ZonedDateTime.of(2026, 10, 9, 9, 0, 0, 0, ZoneOffset.UTC);
        final ZonedDateTime created2 = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);

        saveExport("other-file.zip", created1);
        saveExport("gtfs-passenger-stops.zip", created2);

        final Optional<Instant> result = repository.findLatestCreatedByFileName("gtfs-passenger-stops.zip");

        assertTrue(result.isPresent());
        assertEquals(created2.toInstant(), result.get());
    }

    @Test
    void findLatestCreatedByFileNameReturnsTimestampAsInstant() {
        final ZonedDateTime created = ZonedDateTime.of(2026, 10, 9, 15, 30, 45, 0, ZoneOffset.UTC);
        saveExport("gtfs-passenger-stops.zip", created);

        final Optional<Instant> result = repository.findLatestCreatedByFileName("gtfs-passenger-stops.zip");

        assertTrue(result.isPresent());
        assertEquals(created.toInstant(), result.get());
    }

    @Test
    void findLatestIdByFileNameCreatedBeforeIgnoresNewerAndOtherFiles() {
        final ZonedDateTime cutoff = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);
        saveExport("gtfs-rt-locations", cutoff.minusMinutes(20));
        final GeneratedExport newestExpired = saveExport("gtfs-rt-locations", cutoff.minusMinutes(10));
        saveExport("siri-et.xml", cutoff.minusMinutes(5));
        saveExport("gtfs-rt-locations", cutoff.plusMinutes(1));

        assertEquals(Optional.of(newestExpired.id),
                repository.findLatestIdByFileNameCreatedBefore("gtfs-rt-locations", cutoff));
        assertFalse(repository.findLatestIdByFileNameCreatedBefore("gtfs-rt-updates", cutoff).isPresent());
    }

    @Test
    void deleteBatchByFileNameUpToIdDeletesOnlyThatFileUpToTheIdAndAtMostTheLimit() {
        final ZonedDateTime created = ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);
        saveExport("siri-et.xml", created);
        saveExport("siri-et.xml", created.plusMinutes(1));
        final GeneratedExport third = saveExport("siri-et.xml", created.plusMinutes(2));
        final GeneratedExport newest = saveExport("siri-et.xml", created.plusMinutes(3));
        final GeneratedExport zip = saveExport("gtfs-all.zip", created);

        assertEquals(2, repository.deleteBatchByFileNameUpToId("siri-et.xml", third.id, 2));
        assertEquals(Set.of(third.id, newest.id, zip.id), remainingIds());

        assertEquals(1, repository.deleteBatchByFileNameUpToId("siri-et.xml", third.id, 2));
        assertEquals(Set.of(newest.id, zip.id), remainingIds());

        assertEquals(0, repository.deleteBatchByFileNameUpToId("siri-et.xml", third.id, 2));
    }

    private Set<Long> remainingIds() {
        return repository.findAll().stream().map(export -> export.id).collect(Collectors.toSet());
    }

    private GeneratedExport saveExport(final String fileName, final ZonedDateTime created) {
        final GeneratedExport export = new GeneratedExport();
        export.fileName = fileName;
        export.created = created;
        export.data = new byte[0];
        return repository.save(export);
    }
}

