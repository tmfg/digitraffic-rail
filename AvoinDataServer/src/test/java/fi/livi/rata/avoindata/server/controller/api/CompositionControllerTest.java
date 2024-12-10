package fi.livi.rata.avoindata.server.controller.api;

import java.time.Instant;
import java.util.Random;

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
}