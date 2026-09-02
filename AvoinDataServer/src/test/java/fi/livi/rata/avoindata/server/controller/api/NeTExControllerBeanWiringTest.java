package fi.livi.rata.avoindata.server.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;

/**
 * Verifies that avoindataserver.netex.enabled gates the NeTEx package endpoint.
 */
class NeTExControllerBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void givenPropertyAbsent_whenContextLoads_thenControllerIsActive() {
        // given — no avoindataserver.netex.enabled property (matchIfMissing = true)
        contextRunner.run(context -> assertThat(context).hasSingleBean(NeTExController.class));
    }

    @Test
    void givenPropertyEnabledTrue_whenContextLoads_thenControllerIsActive() {
        // given
        contextRunner
                .withPropertyValues("avoindataserver.netex.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NeTExController.class));
    }

    @Test
    void givenPropertyEnabledFalse_whenContextLoads_thenControllerIsAbsent() {
        // given
        contextRunner
                .withPropertyValues("avoindataserver.netex.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NeTExController.class));
    }

    @Import(NeTExController.class)
    static class TestConfig {

        @Bean
        GeneratedExportRepository generatedExportRepository() {
            return mock(GeneratedExportRepository.class);
        }
    }
}
