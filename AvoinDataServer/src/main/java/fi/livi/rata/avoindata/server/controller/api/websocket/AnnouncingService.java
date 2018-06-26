package fi.livi.rata.avoindata.server.controller.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnnouncingService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SimpMessagingTemplate template;

    @Autowired
    private ObjectMapper objectMapper;

    public String announce(String destination, Object payload) {
        try {
            String payloadAsString = objectMapper.writerWithView(TrainJsonView.LiveTrains.class).writeValueAsString(payload);
            template.convertAndSend(destination, payloadAsString);
            return payloadAsString;
        } catch (Exception e) {
            log.error("Error announcing. Payload: {}, Destination: {}", payload, destination, e);
        }

        return null;
    }
}
