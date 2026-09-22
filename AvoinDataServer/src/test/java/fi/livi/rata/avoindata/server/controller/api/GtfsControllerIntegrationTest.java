package fi.livi.rata.avoindata.server.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;

/**
 * HTTP contract of the static GTFS endpoint: the latest row is served whatever
 * its age, and a missing row is
 * a 404 rather than NPE.
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

        @Test
        void givenDate_whenGet_thenServesThePackageGeneratedThatDay() throws Exception {
                final byte[] zip = "PK-archived".getBytes();
                final ZonedDateTime created = ZonedDateTime.of(2026, 9, 10, 21, 30, 0, 0, DateProvider.ZONE_ID_HKI);
                when(generatedExportRepository
                                .findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
                                                eq(GTFS_PASSENGER_FILENAME), any(ZonedDateTime.class),
                                                any(ZonedDateTime.class)))
                                .thenReturn(export(zip, created));

                mockMvc.perform(get(GTFS_PASSENGER_URL).param("on_date", "2026-09-10"))
                                .andExpect(status().isOk())
                                .andExpect(content().bytes(zip))
                                .andExpect(header().string("x-timestamp", created.toString()));

                // exactly that Helsinki day: start inclusive, next day's start exclusive
                final ArgumentCaptor<ZonedDateTime> from = ArgumentCaptor.forClass(ZonedDateTime.class);
                final ArgumentCaptor<ZonedDateTime> until = ArgumentCaptor.forClass(ZonedDateTime.class);
                verify(generatedExportRepository)
                                .findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
                                                eq(GTFS_PASSENGER_FILENAME), from.capture(), until.capture());
                assertEquals(LocalDate.of(2026, 9, 10).atStartOfDay(DateProvider.ZONE_ID_HKI), from.getValue());
                assertEquals(LocalDate.of(2026, 9, 11).atStartOfDay(DateProvider.ZONE_ID_HKI), until.getValue());

                verify(generatedExportRepository, never()).findFirstByFileNameOrderByIdDesc(any());
        }

        @Test
        void givenDateWithNoGeneration_whenGet_thenNotFound() throws Exception {
                // generation failed that day, so there is no package for it — no falling back
                // to an earlier one
                when(generatedExportRepository
                                .findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
                                                eq(GTFS_PASSENGER_FILENAME), any(ZonedDateTime.class),
                                                any(ZonedDateTime.class)))
                                .thenReturn(null);

                mockMvc.perform(get(GTFS_PASSENGER_URL).param("on_date", "2020-01-01"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void givenUnparseableDate_whenGet_thenBadRequest() throws Exception {
                mockMvc.perform(get(GTFS_PASSENGER_URL).param("on_date", "not-a-date"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void givenPlainDateParam_whenGet_thenRejected() throws Exception {
                // AddableParametersFilter mirrors date into departure_date, which this endpoint
                // does not accept
                mockMvc.perform(get(GTFS_PASSENGER_URL).param("date", "2026-09-10"))
                                .andExpect(status().isBadRequest());
        }

        private static GeneratedExport export(final byte[] data, final ZonedDateTime created) {
                final GeneratedExport export = new GeneratedExport();
                export.data = data;
                export.fileName = GTFS_PASSENGER_FILENAME;
                export.created = created;
                return export;
        }
}
