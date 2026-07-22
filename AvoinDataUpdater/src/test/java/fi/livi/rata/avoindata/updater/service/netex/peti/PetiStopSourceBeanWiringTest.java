package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Spring context slice tests verifying @ConditionalOnProperty wiring of
 * PetiStopSource beans. Uses ApplicationContextRunner for lightweight,
 * fast context creation without starting the full Spring Boot application.
 */
class PetiStopSourceBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // --- C1: Property absent (default/missing) → CachingPetiStopSource bean active ---

    @Test
    void givenPropertyAbsent_whenContextLoads_thenCachingPetiStopSourceIsActive() {
        // given — no "updater.netex.peti.enabled" property set (matchIfMissing=true)
        contextRunner
                .withPropertyValues(
                        "updater.netex.peti.url=https://test.example.com/peti.zip",
                        "updater.netex.peti.block-timeout-seconds=5")
                .run(context -> {
                    // when / then
                    assertThat(context).hasSingleBean(PetiStopSource.class);
                    assertThat(context.getBean(PetiStopSource.class))
                            .isInstanceOf(CachingPetiStopSource.class);
                });
    }

    // --- C2: Property enabled=true explicitly → CachingPetiStopSource bean active ---

    @Test
    void givenPropertyEnabledTrue_whenContextLoads_thenCachingPetiStopSourceIsActive() {
        // given
        contextRunner
                .withPropertyValues(
                        "updater.netex.peti.enabled=true",
                        "updater.netex.peti.url=https://test.example.com/peti.zip",
                        "updater.netex.peti.block-timeout-seconds=5")
                .run(context -> {
                    // when / then
                    assertThat(context).hasSingleBean(PetiStopSource.class);
                    assertThat(context.getBean(PetiStopSource.class))
                            .isInstanceOf(CachingPetiStopSource.class);
                });
    }

    // --- C3: Property enabled=false → EmptyPetiStopSource bean active ---

    @Test
    void givenPropertyEnabledFalse_whenContextLoads_thenEmptyPetiStopSourceIsActive() {
        // given
        contextRunner
                .withPropertyValues(
                        "updater.netex.peti.enabled=false",
                        "updater.netex.peti.url=https://test.example.com/peti.zip",
                        "updater.netex.peti.block-timeout-seconds=5")
                .run(context -> {
                    // when / then
                    assertThat(context).hasSingleBean(PetiStopSource.class);
                    assertThat(context.getBean(PetiStopSource.class))
                            .isInstanceOf(EmptyPetiStopSource.class);
                });
    }

    // --- C4: Exactly one PetiStopSource bean exists (no duplicate) ---

    @Test
    void givenAnyPropertyValue_whenContextLoads_thenExactlyOnePetiStopSourceBean() {
        // given — test with property absent (default)
        contextRunner
                .withPropertyValues(
                        "updater.netex.peti.url=https://test.example.com/peti.zip",
                        "updater.netex.peti.block-timeout-seconds=5")
                .run(context -> {
                    // when / then
                    assertThat(context.getBeansOfType(PetiStopSource.class)).hasSize(1);
                });

        // also test with enabled=false
        contextRunner
                .withPropertyValues(
                        "updater.netex.peti.enabled=false",
                        "updater.netex.peti.url=https://test.example.com/peti.zip",
                        "updater.netex.peti.block-timeout-seconds=5")
                .run(context -> {
                    assertThat(context.getBeansOfType(PetiStopSource.class)).hasSize(1);
                });
    }

    /**
     * Minimal test configuration providing the beans needed for CachingPetiStopSource
     * without starting the full application context.
     */
    @Configuration
    @Import({CachingPetiStopSource.class, EmptyPetiStopSource.class})
    static class TestConfig {

        @Bean
        WebClient webClient() {
            final ExchangeFunction noOp = request -> Mono.empty();
            return WebClient.builder().exchangeFunction(noOp).build();
        }

        @Bean
        PetiNeTExParser petiNeTExParser() {
            return new PetiNeTExParser();
        }
    }
}
