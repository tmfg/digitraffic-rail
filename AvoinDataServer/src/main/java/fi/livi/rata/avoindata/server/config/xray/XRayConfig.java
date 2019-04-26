package fi.livi.rata.avoindata.server.config.xray;

import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.FixedSegmentNamingStrategy;
import org.springframework.context.annotation.Bean;

import javax.servlet.Filter;

public class XRayConfig {
    @Bean
    public Filter TracingFilter() {
        return new AWSXRayServletFilter(new FixedSegmentNamingStrategy("avoindata-server"));
    }
}
