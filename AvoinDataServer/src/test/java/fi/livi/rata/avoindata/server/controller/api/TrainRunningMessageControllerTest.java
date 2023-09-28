package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainRunningMessageFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class TrainRunningMessageControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainRunningMessageFactory trainRunningMessageFactory;

    @Autowired
    private TrainRunningMessageRepository trainRunningMessageRepository;

    @Test
    public void baseFieldsShouldBeCorrect() throws Exception {
        trainRunningMessageFactory.create();

        getJson("/train-tracking/station/PSL/2018-01-01/RAIDE_1")
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].version").value("1"))
                .andExpect(jsonPath("$[0].trainNumber").value("1"))
                .andExpect(jsonPath("$[0].departureDate").value("2018-01-01"))
                .andExpect(jsonPath("$[0].timestamp").value("2018-01-01T06:00:00.000Z"))
                .andExpect(jsonPath("$[0].trackSection").value("RAIDE_1"))
                .andExpect(jsonPath("$[0].station").value("PSL"))
                .andExpect(jsonPath("$[0].type").value("OCCUPY"));
    }

    @Test
    public void trackSectionMatchingShouldWork() throws Exception {
        trainRunningMessageFactory.create();

        assertLength("/train-tracking/station/PSL/2018-01-01/RAIDE_1", 1);
        assertLength("/train-tracking/station/PSL/2018-01-01/RAIDE_2", 0);
        assertLength("/train-tracking/station/HKI/2018-01-01/RAIDE_1", 0);
        assertLength("/train-tracking/station/PSL/2018-01-02/RAIDE_1", 0);
    }

    @Test
    public void trackSectionWithSlashesShouldWork() throws Exception {
        final TrainRunningMessage trainRunningMessage = trainRunningMessageFactory.create();
        trainRunningMessage.trackSection = "PSL/1";
        trainRunningMessageRepository.save(trainRunningMessage);

        final URI url = UriComponentsBuilder.fromUriString("/api/v1/train-tracking/station/PSL/2018-01-01").pathSegment(trainRunningMessage.trackSection).build().encode().toUri();

        getJson(url)
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    public void withoutVersionShouldReturnNewest() throws Exception {
        final TrainRunningMessage trainRunningMessage1 = trainRunningMessageFactory.create();
        final TrainRunningMessage trainRunningMessage2 = trainRunningMessageFactory.create();
        trainRunningMessage2.version = 2L;

        getJson("/train-tracking")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value("2"));
    }
}
