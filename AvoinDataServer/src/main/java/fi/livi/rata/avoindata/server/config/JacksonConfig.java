package fi.livi.rata.avoindata.server.config;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {
    public static final DateTimeFormatter ISO_FIXED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(
            ZoneId.of("Z"));

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return jackson2ObjectMapperBuilder -> {
            jackson2ObjectMapperBuilder.defaultViewInclusion(true);
            jackson2ObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL);
            jackson2ObjectMapperBuilder.indentOutput(false);
            jackson2ObjectMapperBuilder.serializers(new ZonedDateTimeSerializer(ISO_FIXED_FORMAT));
            jackson2ObjectMapperBuilder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            jackson2ObjectMapperBuilder.modules(new Jdk8Module(), new JavaTimeModule(), new JtsModule());
        };
    }
}
