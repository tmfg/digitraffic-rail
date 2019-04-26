package fi.livi.rata.avoindata.server.config.xray;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.FixedSegmentNamingStrategy;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.Filter;

@Configuration
public class XRayConfig {

    @Bean
    public AWSXRayRecorder awsRayRecorder() {
        AWSXRayRecorder recorder = new AWSXRayRecorder();
        recorder.setContextMissingStrategy(new LogErrorContextMissingStrategy());

        AWSXRay.setGlobalRecorder(recorder);
        return recorder;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Filter TracingFilter(AWSXRayRecorder awsRayRecorder) {
        return new AWSXRayServletFilter(new FixedSegmentNamingStrategy("avoindata-server"), awsRayRecorder);
    }
}
