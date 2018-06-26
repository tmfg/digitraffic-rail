package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationConnectionQuality;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import org.osgeo.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrainLocationDeserializer extends AEntityDeserializer<TrainLocation> {

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    private GeometryFactory geometryFactory = new GeometryFactory();

    @Override
    public TrainLocation deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(node.get("junanumero").asLong(), getNodeAsLocalDate(node.get("lahtopaiva")),
                getNodeAsDateTime(node.get("aikaleima")));
        trainLocation.speed = node.get("nopeus").asInt();
        trainLocation.connectionQuality = getConnectionQuality(node.get("yhteys").textValue());

        final double iKoordinaatti = node.get("sijainti").get("longitude").asDouble();
        final double pKoordinaatti = node.get("sijainti").get("latitude").asDouble();
        final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(iKoordinaatti, pKoordinaatti);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(projCoordinate.x, projCoordinate.y));

        trainLocation.liikeLocation = geometryFactory.createPoint(new Coordinate(iKoordinaatti, pKoordinaatti));

        return trainLocation;
    }

    private TrainLocationConnectionQuality getConnectionQuality(final String yhteys) {
        if (yhteys.equals("OK")) {
            return TrainLocationConnectionQuality.OK;
        } else if (yhteys.equals("KATKONAINEN")) {
            return TrainLocationConnectionQuality.BREAKING;
        } else if (yhteys.equals("POIKKI")) {
            return TrainLocationConnectionQuality.LOST;
        } else {
            throw new IllegalArgumentException("Could not parse TrainLocationConnectionQuality from " + yhteys);
        }
    }
}
