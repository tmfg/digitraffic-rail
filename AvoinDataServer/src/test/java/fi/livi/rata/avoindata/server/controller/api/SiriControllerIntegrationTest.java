package fi.livi.rata.avoindata.server.controller.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;

/**
 * REST leg of the SIRI-ET pipeline: {@code GET /api/v1/siri/et} serves the latest {@code siri-et.xml}
 * {@link GeneratedExport} row (produced by the updater) as {@code application/xml}, sets the freshness headers,
 * and returns 404 when nothing has been generated yet.
 *
 * <p>The end-to-end DB round-trip (NeTEx → DB → SIRI-ET → DB) is covered by
 * {@code SiriEtDbIntegrationTest} in the updater. The server datasource is read-only by design, so here the
 * {@link GeneratedExportRepository} is stubbed to focus on the controller's HTTP contract with the full web
 * context (MockMvc, content negotiation, headers, status).
 */
// The Secrets Manager region is supplied by the runtime environment, not the repo — disable it so the context
// loads hermetically; a static region satisfies any remaining AWS client. The test application.properties
// shadows the main one, so the SIRI-ET endpoint must be re-enabled here for it to be mapped.
@TestPropertySource(properties = {
        "avoindataserver.siri.et.enabled=true",
        "spring.cloud.aws.secretsmanager.enabled=false",
        "spring.cloud.aws.region.static=eu-west-1"
})
class SiriControllerIntegrationTest extends MockMvcBaseTest {

    private static final String SIRI_ET_URL = "/api/v1/siri/et";
    private static final String ET_FILENAME = "siri-et.xml";

    @MockitoBean
    private GeneratedExportRepository generatedExportRepository;

    @Test
    void givenFreshSiriEt_whenGet_thenServesXmlWithFreshHeaders() throws Exception {
        final byte[] xml = "<Siri><ServiceDelivery/></Siri>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(ET_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki()));

        mockMvc.perform(get(SIRI_ET_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                // NB: a global ContentTypeInterceptor overrides the response content type to application/json
                // app-wide, so we assert on the served bytes + headers rather than the content type.
                .andExpect(content().bytes(xml))
                .andExpect(header().string("x-is-fresh", "true"))
                .andExpect(header().exists("x-timestamp"))
                .andExpect(header().string("Content-Length", String.valueOf(xml.length)));
    }

    @Test
    void givenStaleSiriEt_whenGet_thenServesXmlButNotFresh() throws Exception {
        final byte[] xml = "<Siri/>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(ET_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki().minusMinutes(10)));

        mockMvc.perform(get(SIRI_ET_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(header().string("x-is-fresh", "false"));
    }

    @Test
    void givenNoPublishedSiriEt_whenGet_thenNotFound() throws Exception {
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(ET_FILENAME))).thenReturn(null);

        mockMvc.perform(get(SIRI_ET_URL))
                .andExpect(status().isNotFound());
    }

    private static GeneratedExport export(final byte[] data, final java.time.ZonedDateTime created) {
        final GeneratedExport export = new GeneratedExport();
        export.data = data;
        export.fileName = ET_FILENAME;
        export.created = created;
        return export;
    }
}
