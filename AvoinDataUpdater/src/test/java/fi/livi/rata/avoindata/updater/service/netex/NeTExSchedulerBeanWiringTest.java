package fi.livi.rata.avoindata.updater.service.netex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Verifies that updater.netex.enabled gates scheduled NeTEx generation.
 */
class NeTExSchedulerBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void givenPropertyAbsent_whenContextLoads_thenSchedulerIsActive() {
        // given — no updater.netex.enabled property (matchIfMissing = true)
        contextRunner.run(context -> assertThat(context).hasSingleBean(NeTExScheduler.class));
    }

    @Test
    void givenPropertyEnabledTrue_whenContextLoads_thenSchedulerIsActive() {
        // given
        contextRunner
                .withPropertyValues("updater.netex.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NeTExScheduler.class));
    }

    @Test
    void givenPropertyEnabledFalse_whenContextLoads_thenSchedulerIsAbsent() {
        // given
        contextRunner
                .withPropertyValues("updater.netex.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NeTExScheduler.class));
    }

    /**
     * Plain class rather than {@code @Configuration} for the same reason as
     * {@code PetiStopSourceBeanWiringTest}: the application's explicit
     * {@code @ComponentScan} would otherwise leak these beans into every
     * {@code @SpringBootTest} context.
     */
    @Import(NeTExScheduler.class)
    static class TestConfig {

        @Bean
        NeTExPackageService neTExPackageService() {
            return mock(NeTExPackageService.class);
        }
    }
}
