package fi.livi.rata.avoindata.updater.config;

import java.util.Random;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@IntegrationComponentScan
public class MQTTConfig {
    @Value("${updater.mqtt.server-url}")
    private String mqttServerUrl;

    @Value("${updater.mqtt.client-id}")
    private String mqttClientId;

    @Value("${updater.mqtt.username}")
    private String mqtt_username;

    @Value("${updater.mqtt.password}")
    private String mqtt_password;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        final DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        final MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});
        options.setUserName(mqtt_username);
        options.setPassword(mqtt_password.toCharArray());
        options.setConnectionTimeout(10);

        factory.setConnectionOptions(options);

        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound(final MqttPahoClientFactory mqttPahoClientFactory) {
        final MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(mqttClientId+new Random().nextInt(1000),
                mqttPahoClientFactory);
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
    public interface MQTTGateway {
        void sendToMqtt(final Message<String> data);
    }
}
