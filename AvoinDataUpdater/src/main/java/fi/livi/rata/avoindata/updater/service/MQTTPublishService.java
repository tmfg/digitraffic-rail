package fi.livi.rata.avoindata.updater.service;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.updater.config.MQTTConfig;

@Service
public class MQTTPublishService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MQTTConfig.MQTTGateway MQTTGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    public synchronized <E> void publish(Function<E, String> topicProvider, List<E> entities) {
        this.publish(topicProvider, entities, null);
    }

    public synchronized <E> void publish(Function<E, String> topicProvider, List<E> entities, Class viewClass) {
        for (final E entity : entities) {
            publishEntity(topicProvider.apply(entity), entity, viewClass);
        }
    }

    public synchronized Message<String> publishString(String topic, String entity) {
        try {
            String entityAsString = entity;

            final MessageBuilder<String> payloadBuilder = MessageBuilder.withPayload(entityAsString);

            final String fullTopic = getFullTopic(topic);

            final Message<String> message = payloadBuilder.setHeader(MqttHeaders.TOPIC, fullTopic).build();
            try {
                MQTTGateway.sendToMqtt(message);
            } catch (Exception e) {
                log.error("Error sending data to MQTT. Topic: {}, Entity: {}", topic, entity);
            }

            return message;
        } catch (Exception e) {
            log.error("Error publishing {} to {}", topic, entity);
            return null;
        }
    }

    public synchronized <E> Message<String> publishEntity(String topic, E entity, Class viewClass) {
        try {
            String entityAsString = getEntityAsString(entity, viewClass);

            return publishString(topic, entityAsString);
        } catch (Exception e) {
            log.error("Error publishing {} to {}", topic, entity);
        }
        return null;
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
