package fi.livi.rata.avoindata.server.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {
    public static final DateTimeFormatter ISO_FIXED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(
            ZoneId.of("Z"));

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                    .disable(SerializationFeature.INDENT_OUTPUT)
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .enable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
                    .addModule(new JtsModule())
                    .addModule(new StreamModule());
        };
    }
}
