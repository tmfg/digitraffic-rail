package fi.livi.rata.avoindata.server.config.xray;

import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.FixedSegmentNamingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.Filter;

@Configuration
public class XRayConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter TracingFilter() {
        return new AWSXRayServletFilter(new FixedSegmentNamingStrategy("avoindata-server"));
    }
}
