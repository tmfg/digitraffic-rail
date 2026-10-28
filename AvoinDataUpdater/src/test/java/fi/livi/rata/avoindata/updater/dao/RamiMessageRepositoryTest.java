package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.rami.RamiFirstCreated;
import fi.livi.rata.avoindata.common.dao.rami.RamiMessageRepository;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.updater.BaseTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
public class RamiMessageRepositoryTest extends BaseTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private RamiMessageRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    public void clean() {
        entityManager.createNativeQuery("delete from rami_message").executeUpdate();
    }

    private void insert(final String id, final int version, final String type, final String created, final String start,
                        final String end, final String deleted) {
        entityManager.createNativeQuery("""
                insert into rami_message (id, version, message_type, created_source, start_validity, end_validity, deleted)
                values (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                """)
                .setParameter(1, id).setParameter(2, version).setParameter(3, type).setParameter(4, created)
                .setParameter(5, start).setParameter(6, end).setParameter(7, deleted)
                .executeUpdate();
    }

    private void insertTexts(final String id, final int version) {
        entityManager.createNativeQuery("insert into rami_message_video (rami_message_id, rami_message_version, text_fi, text_sv) values (?1, ?2, 'Moi', 'Hej')")
                .setParameter(1, id).setParameter(2, version).executeUpdate();
        entityManager.createNativeQuery("insert into rami_message_audio (rami_message_id, rami_message_version, text_fi) values (?1, ?2, 'Puhe')")
                .setParameter(1, id).setParameter(2, version).executeUpdate();
        entityManager.createNativeQuery("insert into rami_message_station (rami_message_id, rami_message_version, station_short_code) values (?1, ?2, 'HKI'), (?1, ?2, 'TPE')")
                .setParameter(1, id).setParameter(2, version).executeUpdate();
    }

    @Test
    public void onlyLatestActiveVersionsAreFound() {
        final String t = "SCHEDULED_MESSAGE";
        insert("A", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", null);
        insert("A", 2, t, "2026-10-27 09:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", null);
        insertTexts("A", 2);
        insert("B", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-27 11:00:00", null);
        insert("C", 1, t, "2026-10-27 08:00:00", "2026-10-27 13:00:00", "2026-10-28 00:00:00", null);
        insert("D", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", "2026-10-27 10:00:00");
        insert("E", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", "2026-10-26 10:00:00");
        insert("F", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-27 09:00:00", "2026-10-27 11:00:00");
        entityManager.flush();
        entityManager.clear();

        final List<RamiMessage> messages = repository.findLatestActive(NOW, NOW.minusHours(5));

        assertEquals(List.of("A", "D"), messages.stream().map(m -> m.id).sorted().toList());
        final RamiMessage a = messages.stream().filter(m -> m.id.equals("A")).findFirst().orElseThrow();
        assertEquals(2, a.version);
        assertEquals(1, a.videos.size());
        assertEquals("Hej", a.videos.iterator().next().textSv);
        assertEquals(1, a.audios.size());
        assertEquals(2, a.stations.size());
        assertEquals(NOW.minusHours(3), a.createdSource);
    }

    @Test
    public void firstCreatedIsFromFirstVersion() {
        final String t = "SCHEDULED_MESSAGE";
        insert("A", 1, t, "2026-10-27 08:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", null);
        insert("A", 2, t, "2026-10-27 09:00:00", "2026-10-27 08:00:00", "2026-10-28 00:00:00", null);
        entityManager.flush();
        entityManager.clear();

        final List<RamiFirstCreated> result = repository.findFirstCreated(List.of("A"));

        assertEquals(1, result.size());
        assertTrue(result.get(0).getCreated().isEqual(NOW.minusHours(4)));
    }
}
