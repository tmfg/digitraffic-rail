package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;

@Component
public class TrainLocationDeserializer extends AEntityDeserializer<TrainLocation> {

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    private GeometryFactory geometryFactory = new GeometryFactory();

    @Override
    public TrainLocation deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(node.get("junanumero").asLong(), getNodeAsLocalDate(node.get("lahtopaiva")),
                getNodeAsDateTime(node.get("aikaleima")));
        trainLocation.speed = node.get("nopeus").asInt();

        final double iKoordinaatti = node.get("sijainti").get("longitude").asDouble();
        final double pKoordinaatti = node.get("sijainti").get("latitude").asDouble();
        trainLocation.liikeLocation = geometryFactory.createPoint(new Coordinate(iKoordinaatti, pKoordinaatti));

        final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(iKoordinaatti, pKoordinaatti);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(projCoordinate.x, projCoordinate.y));

        trainLocation.accuracy = getNullableInteger(node, "tarkkuus");

        return trainLocation;
    }
}
