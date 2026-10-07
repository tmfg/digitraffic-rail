package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;

/**
 * Verifies the {@code avoindataserver.siri.et.enabled} flag actually gates the endpoint's existence, not just
 * its data: with the flag left at its test-default (unset, i.e. disabled — see the test {@code
 * application.properties}, which shadows the main one and does not set it), {@link SiriEtController} is a
 * {@code @ConditionalOnProperty} bean that is never registered, so the route isn't mapped at all and Spring
 * itself returns 404 (distinct from {@link SiriEtControllerIntegrationTest}'s "enabled but nothing generated
 * yet" 404, which comes from the controller's own logic).
 * <p>
 * Deliberately does <em>not</em> set the property to {@code false} via {@code @TestPropertySource}: {@code
 * @ConditionalOnProperty(havingValue = "true")} without {@code matchIfMissing} already defaults to disabled
 * when the property is absent, so leaving it unset exercises exactly that real-world "not configured" path —
 * the one actual deployments hit before anyone opts in — rather than a synthetic "explicitly disabled" case
 * nothing in production ever sets.
 */
class SiriEtControllerDisabledIntegrationTest extends MockMvcBaseTest {

    @Test
    void givenSiriEtDisabled_whenGet_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/siri/et"))
                .andExpect(status().isNotFound());
    }
}
