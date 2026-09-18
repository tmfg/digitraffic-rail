package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;

/**
 * Verifies the {@code avoindataserver.siri.vm.enabled} flag actually gates the endpoint's existence, not just
 * its data: with the flag left at its test-default (unset, i.e. disabled — see the test {@code
 * application.properties}, which shadows the main one and does not set it), {@link SiriVmController} is a
 * {@code @ConditionalOnProperty} bean that is never registered, so the route isn't mapped at all and Spring
 * itself returns 404 (distinct from {@link SiriVmControllerIntegrationTest}'s "enabled but nothing generated
 * yet" 404, which comes from the controller's own logic).
 */
@TestPropertySource(properties = {
        "spring.cloud.aws.secretsmanager.enabled=false",
        "spring.cloud.aws.region.static=eu-west-1"
})
class SiriVmControllerDisabledIntegrationTest extends MockMvcBaseTest {

    @Test
    void givenSiriVmDisabled_whenGet_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/siri/vm"))
                .andExpect(status().isNotFound());
    }
}
