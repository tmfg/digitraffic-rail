package fi.livi.rata.avoindata.updater.service.rami;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@Service
public class RamiValidationService {

    private final JsonSchema ramiMessageSchema;

    private final JsonSchemaFactory jsonSchemaFactory;

    public RamiValidationService(
            @Value("classpath:schema/ramiOperatorScheduledMessage.json")
            final Resource ramiMessageSchema) throws IOException {
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setHandleNullableField(true);
        this.jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.ramiMessageSchema = jsonSchemaFactory.getSchema(ramiMessageSchema.getInputStream(), config);
    }

    public Set<ValidationMessage> validateRamiMessage(final JsonNode requestBody) {
        return ramiMessageSchema.validate(requestBody);
    }

}
