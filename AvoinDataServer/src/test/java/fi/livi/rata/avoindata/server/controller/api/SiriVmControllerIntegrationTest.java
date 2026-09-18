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
 * REST leg of the SIRI-VM pipeline: {@code GET /api/v1/siri/vm} serves the latest {@code siri-vm.xml}
 * {@link GeneratedExport} row (produced by the updater) as {@code application/xml}, sets the freshness headers,
 * and returns 404 when nothing has been generated yet (see {@link #givenNoPublishedSiriVm_whenGet_thenNotFound()}
 * for that "enabled but nothing generated yet" case; the separate "flag off entirely, no route mapped" case is
 * covered by {@link SiriVmControllerDisabledIntegrationTest}). Mirrors {@link SiriEtControllerIntegrationTest}.
 */
// spring.cloud.aws.secretsmanager.enabled=false is already set globally in the test application.properties.
// A static region still needs to be supplied here for any remaining AWS client. The test application.properties
// shadows the main one, so the SIRI-VM endpoint must be re-enabled here for it to be mapped.
@TestPropertySource(properties = {
        "avoindataserver.siri.vm.enabled=true",
        "spring.cloud.aws.region.static=eu-west-1"
})
class SiriVmControllerIntegrationTest extends MockMvcBaseTest {

    private static final String SIRI_VM_URL = "/api/v1/siri/vm";
    private static final String VM_FILENAME = "siri-vm.xml";

    @MockitoBean
    private GeneratedExportRepository generatedExportRepository;

    @Test
    void givenFreshSiriVm_whenGet_thenServesXmlWithFreshHeaders() throws Exception {
        final byte[] xml = "<Siri><ServiceDelivery/></Siri>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(VM_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki()));

        mockMvc.perform(get(SIRI_VM_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().bytes(xml))
                .andExpect(header().string("x-is-fresh", "true"))
                .andExpect(header().exists("x-timestamp"))
                .andExpect(header().string("Content-Length", String.valueOf(xml.length)));
    }

    @Test
    void givenStaleSiriVm_whenGet_thenServesXmlButNotFresh() throws Exception {
        final byte[] xml = "<Siri/>".getBytes(StandardCharsets.UTF_8);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(VM_FILENAME)))
                .thenReturn(export(xml, DateProvider.nowInHelsinki().minusMinutes(10)));

        mockMvc.perform(get(SIRI_VM_URL).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(header().string("x-is-fresh", "false"));
    }

    @Test
    void givenNoPublishedSiriVm_whenGet_thenNotFound() throws Exception {
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc(eq(VM_FILENAME))).thenReturn(null);

        mockMvc.perform(get(SIRI_VM_URL))
                .andExpect(status().isNotFound());
    }

    private static GeneratedExport export(final byte[] data, final java.time.ZonedDateTime created) {
        final GeneratedExport export = new GeneratedExport();
        export.data = data;
        export.fileName = VM_FILENAME;
        export.created = created;
        return export;
    }
}
