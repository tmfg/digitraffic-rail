package fi.livi.rata.avoindata.LiikeInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class LiikeInterfaceApplication {
    private static Logger log = LoggerFactory.getLogger(LiikeInterfaceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(applicationClass, args);
    }

    private static Class<LiikeInterfaceApplication> applicationClass = LiikeInterfaceApplication.class;
}
