package fi.livi.rata.avoindata.updater.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.updater.config.MQTTConfig;

@Service
public class MQTTPublishService {
    private static final int QUEUE_SIZE = 150;
    public static final int NUMBER_OF_THREADS = 1;
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MQTTConfig.MQTTGateway MQTTGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    private ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @PostConstruct
    public void setup() {
        executor.setQueueCapacity(QUEUE_SIZE);
        executor.setMaxPoolSize(NUMBER_OF_THREADS);
        executor.setThreadNamePrefix("mqtt-send");
        executor.initialize();
    }

    public <E> void publish(Function<E, String> topicProvider, List<E> entities) {
        this.publish(topicProvider, entities, null);
    }

    public <E> void publish(Function<E, String> topicProvider, List<E> entities, Class viewClass) {
        for (final E entity : entities) {
            publishEntity(topicProvider.apply(entity), entity, viewClass);
        }
    }

    public <E> Future<Message<String>> publishEntity(String topic, E entity, Class viewClass) {
        try {
            String entityAsString = getEntityAsString(entity, viewClass);

            return publishString(topic, entityAsString);
        } catch (Exception e) {
            log.error("Error publishing {} to {}", topic, entity);
        }
        return null;
    }

    public Future<Message<String>> publishString(String topic, String entity) {
        try {
            String entityAsString = entity;

            final MessageBuilder<String> payloadBuilder = MessageBuilder.withPayload(entityAsString);

            final String topicToPublishTo = getReplacedTopic(getFullTopic(topic));

            final Message<String> message = payloadBuilder.setHeader(MqttHeaders.TOPIC, topicToPublishTo).build();

            ZonedDateTime submittedAt = ZonedDateTime.now();
            Future<Message<String>> future = executor.submit(() -> {

                try {
                    Thread.sleep(1000);
                    ZonedDateTime executionStartedAt = ZonedDateTime.now();
                    MQTTGateway.sendToMqtt(message);
                    Thread.sleep(4000);
                    if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                        Logger logger = LoggerFactory.getLogger("MQTT-update");
                        logger.info("Waited: {}, Executed: {}", Duration.between(submittedAt, executionStartedAt),
                                Duration.between(executionStartedAt, ZonedDateTime.now()));
                    }
                    return message;
                } catch (Exception e) {
                    log.error("Error sending data to MQTT. Topic: {}, Entity: {}", topic, entity, e);
                    return null;
                }
            });

            return future;
        } catch (Exception e) {
            log.error("Error publishing {} to {}", topic, entity, e);
            return null;
        }
    }

    private String getReplacedTopic(String topic) {
        return topic.replace("+", "").replace("#", "").replaceAll("/null/", "//").replaceAll("/null/", "//").replaceFirst("/null$", "/");
    }

    private <E> String getEntityAsString(E entity, Class viewClass) throws JsonProcessingException {
        String entityAsString;
        if (viewClass != null) {
            entityAsString = objectMapper.writerWithView(viewClass).writeValueAsString(entity);
        } else {
            entityAsString = objectMapper.writeValueAsString(entity);
        }
        return entityAsString;
    }

    private String getFullTopic(String topic) {
        String prefix = "";
        if (!Sets.newHashSet(environment.getActiveProfiles()).contains("prd")) {
            prefix = Joiner.on(",").join(environment.getActiveProfiles());
        }

        if (Strings.isNullOrEmpty(prefix)) {
            return topic;
        } else {
            return String.format("%s/%s", prefix, topic);
        }


    }


}
