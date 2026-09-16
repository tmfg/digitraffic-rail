package fi.livi.rata.avoindata.server.controller.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;

/**
 * HTTP contract of the static GTFS endpoint: the newest row is served whatever its age, and a missing row is
 * a 404 rather than the NPE it used to be.
 */
@TestPropertySource(properties = {
        "spring.cloud.aws.secretsmanager.enabled=false",
        "spring.cloud.aws.region.static=eu-west-1"
})
class GtfsControllerIntegrationTest extends MockMvcBaseTest {

    private static final String GTFS_PASSENGER_URL = "/api/v1/trains/gtfs-passenger.zip";
    private static final String GTFS_PASSENGER_FILENAME = "gtfs-passenger.zip";

    @MockitoBean
    private GeneratedExportRepository generatedExportRepository;

    @Test
    void givenFreshPackage_whenGet_thenServesZipAndIsFresh() throws Exception {
        final byte[] zip = "PK-fresh".getBytes();
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(GTFS_PASSENGER_FILENAME)))
                .thenReturn(export(zip, DateProvider.nowInHelsinki()));

        mockMvc.perform(get(GTFS_PASSENGER_URL))
                .andExpect(status().isOk())
                .andExpect(content().bytes(zip))
                .andExpect(header().string("x-is-fresh", "true"))
                .andExpect(header().exists("x-timestamp"));
    }

    @Test
    void givenYesterdaysPackage_whenGet_thenStillServesItButNotFresh() throws Exception {
        // a failed overnight generation must not stop the previous package being served
        final byte[] zip = "PK-stale".getBytes();
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(GTFS_PASSENGER_FILENAME)))
                .thenReturn(export(zip, DateProvider.nowInHelsinki().minusHours(30)));

        mockMvc.perform(get(GTFS_PASSENGER_URL))
                .andExpect(status().isOk())
                .andExpect(content().bytes(zip))
                .andExpect(header().string("x-is-fresh", "false"));
    }

    @Test
    void givenNoPublishedPackage_whenGet_thenNotFound() throws Exception {
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(GTFS_PASSENGER_FILENAME)))
                .thenReturn(null);

        mockMvc.perform(get(GTFS_PASSENGER_URL))
                .andExpect(status().isNotFound());
    }

    private static GeneratedExport export(final byte[] data, final ZonedDateTime created) {
        final GeneratedExport export = new GeneratedExport();
        export.data = data;
        export.fileName = GTFS_PASSENGER_FILENAME;
        export.created = created;
        return export;
    }
}
