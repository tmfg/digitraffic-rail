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
 * REST leg of the SIRI-SX pipeline: {@code GET /api/v1/siri/sx} serves the latest {@code siri-sx.xml}
 * {@link GeneratedExport} row (produced by the updater) as {@code application/xml}, sets the freshness headers,
 * and returns 404 when nothing has been generated yet (see {@link #givenNoPublishedSiriSx_whenGet_thenNotFound()}
 * for that "enabled but nothing generated yet" case; the separate "flag off entirely, no route mapped" case is
 * covered by {@link SiriSxControllerDisabledIntegrationTest}). Mirrors {@link SiriVmControllerIntegrationTest}.
 */
// The test application.properties shadows the main one, so the SIRI-SX endpoint must be re-enabled here for
// it to be mapped.
@TestPropertySource(properties = {
        "avoindataserver.siri.sx.enabled=true"
})
class SiriSxControllerIntegrationTest extends MockMvcBaseTest {

    private static final String SIRI_SX_URL = "/api/v1/siri/sx";
    private static final String SX_FILENAME = "siri-sx.xml";

    @MockitoBean
    private GeneratedExportRepository generatedExportRepository;

    @Test
    void givenFreshSiriSx_whenGet_thenServesXmlWithFreshHeaders() throws Exception {
        final byte[] xml = "<Siri><ServiceDelivery/></Siri>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(SX_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki()));

        mockMvc.perform(get(SIRI_SX_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().bytes(xml))
                .andExpect(header().string("x-is-fresh", "true"))
                .andExpect(header().exists("x-timestamp"))
                .andExpect(header().string("Content-Length", String.valueOf(xml.length)));
    }

    @Test
    void givenStaleSiriSx_whenGet_thenServesXmlButNotFresh() throws Exception {
        final byte[] xml = "<Siri/>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(SX_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki().minusMinutes(10)));

        mockMvc.perform(get(SIRI_SX_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(header().string("x-is-fresh", "false"));
    }

    @Test
    void givenNoPublishedSiriSx_whenGet_thenNotFound() throws Exception {
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(SX_FILENAME))).thenReturn(null);

        mockMvc.perform(get(SIRI_SX_URL))
                .andExpect(status().isNotFound());
    }

    private static GeneratedExport export(final byte[] data, final java.time.ZonedDateTime created) {
        final GeneratedExport export = new GeneratedExport();
        export.data = data;
        export.fileName = SX_FILENAME;
        export.created = created;
        return export;
    }
}
