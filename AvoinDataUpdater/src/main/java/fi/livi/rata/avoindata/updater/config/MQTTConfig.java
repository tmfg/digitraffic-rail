package fi.livi.rata.avoindata.updater.config;

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
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});
        options.setUserName(mqtt_username);
        options.setPassword(mqtt_password.toCharArray());

        factory.setConnectionOptions(options);

        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound(MqttPahoClientFactory mqttPahoClientFactory) {
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(mqttClientId, mqttPahoClientFactory);
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
    public interface TrainLocationGateway {

        void sendToMqtt(Message data);

    }
}
