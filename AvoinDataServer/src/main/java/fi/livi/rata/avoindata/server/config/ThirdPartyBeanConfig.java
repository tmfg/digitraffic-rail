package fi.livi.rata.avoindata.server.config;

import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ThirdPartyBeanConfig {

    @Bean
    public BatchExecutionService batchExecutionService() {
        return new BatchExecutionService();
    }
}
