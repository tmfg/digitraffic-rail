package fi.livi.rata.avoindata.server.controller.api;

import java.time.Instant;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.CompositionFactory;

public class CompositionControllerTest extends MockMvcBaseTest {
    @Autowired
    private CompositionFactory compositionFactory;

    @Autowired
    private CompositionRepository compositionRepository;

    final Random random = new Random();

    @Test
    @Transactional
    public void versionSearchShouldWork() throws Exception {
        final long version = Instant.now().toEpochMilli();
        final Instant messageDateTime = Instant.ofEpochSecond(random.nextLong(0L, 2208988800L));
        compositionFactory.create(version, messageDateTime);

        assertLength(StringUtil.format("/compositions?version={}", version-1), 1);
        assertLength(StringUtil.format("/compositions?version={}", version), 0);
        assertLength(StringUtil.format("/compositions?version={}", version+1), 0);

        Assertions.assertEquals(messageDateTime, compositionRepository.getMaxMessageDateTime());
    }

    @Test
    @Transactional
    public void versionSearchShouldWorkOver1000() throws Exception {
        final long version100 = 1001;
        final long version200 = 1002;
        final long version1000 = 1003;
        final long version1001 = 1004;
        final long version900 = 1005;
        compositionFactory.createVersions(
                Pair.of(version100, 100),
                Pair.of(version200, 200),
                Pair.of(version1000, 1000),
                Pair.of(version1001, 1001),
                Pair.of(version900, 900));

        assertLengthAndVersion(StringUtil.format("/compositions?version={}", version100-1), 100+200, version100, version200); // returns all from version100 + version200 and dumps others
        assertLengthAndVersion(StringUtil.format("/compositions?version={}", version100), 200, version200, version200); // returns all from version200 and dumps others
        assertLengthAndVersion(StringUtil.format("/compositions?version={}", version200), 1000, version1000, version1000); // returns all from version1000 and dumps others
        assertLengthAndVersion(StringUtil.format("/compositions?version={}", version1000), 1001, version1001, version1001); // returns version1001 only as it has over 1000 items
        assertLengthAndVersion(StringUtil.format("/compositions?version={}", version1001), 900, version900, version900); // returns version900
        assertLength(StringUtil.format("/compositions?version={}", version900), 0);
    }
}