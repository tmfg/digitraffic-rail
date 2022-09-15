package fi.livi.rata.avoindata.updater.service.rami;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@Service
public class RamiValidationService {

    private final Resource ramiMessageSchema;

    private final Resource ramiSituationSchema;

    private final JsonSchemaFactory jsonSchemaFactory;

    public RamiValidationService(
            @Value("classpath:schema/externalScheduledMessageKafka.json") final Resource ramiMessageSchema,
            @Value("classpath:schema/situationExchangeDeliveryMessage.json") final Resource ramiSituationSchema) {
        this.ramiMessageSchema = ramiMessageSchema;
        this.ramiSituationSchema = ramiSituationSchema;
        this.jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);;
    }

    public Set<ValidationMessage> validateRamiMessage(final JsonNode requestBody) throws IOException {
        final JsonSchema jsonSchema = jsonSchemaFactory.getSchema(ramiMessageSchema.getInputStream());
        return jsonSchema.validate(requestBody);
    }

    public Set<ValidationMessage> validateRamiSituation(final JsonNode requestBody) throws IOException {
        final JsonSchema jsonSchema = jsonSchemaFactory.getSchema(ramiSituationSchema.getInputStream());
        return jsonSchema.validate(requestBody);
    }
}
