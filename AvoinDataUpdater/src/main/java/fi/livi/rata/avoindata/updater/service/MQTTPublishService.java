package fi.livi.rata.avoindata.updater.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.updater.config.MQTTConfig;

@Service
public class MQTTPublishService {
    private static final int QUEUE_SIZE = 50000;
    public static final int NUMBER_OF_THREADS = 1;
    private static final Logger log = LoggerFactory.getLogger(MQTTPublishService.class);

    @Autowired
    private MQTTConfig.MQTTGateway MQTTGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mqtt.enable:true}")
    private boolean enableMqtt;

    private ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @PostConstruct
    public void setup() {
        executor.setQueueCapacity(QUEUE_SIZE);
        executor.setMaxPoolSize(NUMBER_OF_THREADS);
        executor.setThreadNamePrefix("mqtt-send");
        executor.initialize();
    }

    public <E> void publish(final Function<E, String> topicProvider, final List<E> entities) {
        this.publish(topicProvider, entities, null);
    }

    public <E> void publish(final Function<E, String> topicProvider, final List<E> entities, final Class viewClass) {
        for (final E entity : entities) {
            publishEntity(topicProvider.apply(entity), entity, viewClass);
        }
    }

    public <E> Future<Message<String>> publishEntity(final String topic, final E entity, final Class viewClass) {
        try {
            final String entityAsString = getEntityAsString(entity, viewClass);

            return publishString(topic, entityAsString);
        } catch (final Exception e) {
            log.error("Error publishing %s to %s".format(topic, entity), e);
        }

        return null;
    }

    private void publishMessage(final Message<String> message) {
        if(enableMqtt) {
            MQTTGateway.sendToMqtt(message);
        }
    }

    public Future<Message<String>> publishString(final String topic, final String entity) {
        try {
            final Message<String> message = buildMessage(topic, entity);

            final ZonedDateTime submittedAt = ZonedDateTime.now();

            final Future<Message<String>> future = executor.submit(() -> {
                try {
                    final ZonedDateTime executionStartedAt = ZonedDateTime.now();

                    publishMessage(message);

                    if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                        log.info("Waited: {}, Executed: {}", Duration.between(submittedAt, executionStartedAt),
                                Duration.between(executionStartedAt, ZonedDateTime.now()));
                    }
                    return message;
                } catch (final Exception e) {
                    log.error("Error sending data to MQTT. Topic: {}", topic, e);
                    return null;
                }
            });

            return future;
        } catch (final Exception e) {
            log.error("Error publishing to: " + topic, e);
            return null;
        }
    }

    private Message<String> buildMessage(final String topic, final String entity) {
        final MessageBuilder<String> payloadBuilder = MessageBuilder.withPayload(entity);

        final String topicToPublishTo = getReplacedTopic(topic);

        return payloadBuilder.setHeader(MqttHeaders.TOPIC, topicToPublishTo).build();
    }

    private String getReplacedTopic(final String topic) {
        return topic.replace("+", "").replace("#", "").replaceAll("/null/", "//").replaceAll("/null/", "//").replaceFirst("/null$", "/");
    }

    private <E> String getEntityAsString(final E entity, final Class viewClass) throws JsonProcessingException {
        final String entityAsString;
        if (viewClass != null) {
            entityAsString = objectMapper.writerWithView(viewClass).writeValueAsString(entity);
        } else {
            entityAsString = objectMapper.writeValueAsString(entity);
        }
        return entityAsString;
    }
}
