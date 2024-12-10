package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;

import fi.finrail.koju.model.OsavalinVaunuDto;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;

@Component
public class WagonDeserializer extends AEntityDeserializer<Wagon> {
    private static final Logger log = LoggerFactory.getLogger(WagonDeserializer.class);

    private static final Pattern EVN_PATTERN = Pattern.compile("^[0-9]{11}-[0-9]$");

    private final String[] typesForWhichVehicleNumberPublishingIsAllowed;

    public WagonDeserializer(
            @Value("${updater.typesForVehicleNumberPublishinIsAllowed}")
            final String typesForWhichVehicleNumberPublishingIsAllowed) {
        this.typesForWhichVehicleNumberPublishingIsAllowed = typesForWhichVehicleNumberPublishingIsAllowed.split(",");
        log.info("method=WagonDeserializer typesForWhichVehicleNumberPublishingIsAllowed: {}", typesForWhichVehicleNumberPublishingIsAllowed);
    }

    @Override
    public Wagon deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final Wagon wagon = new Wagon();
        wagon.location = node.get("sijainti").asInt();
        wagon.salesNumber = node.get("myyntinumero").asInt();
        wagon.length = node.get("pituus").asInt();
        wagon.playground = nullIfFalse(node.get("leikkitila"));
        wagon.pet = nullIfFalse(node.get("lemmikkielainosasto"));
        wagon.catering = nullIfFalse(node.get("kahvio"));
        wagon.video = nullIfFalse(node.get("video"));
        wagon.luggage = nullIfFalse(node.get("tilaaMatkalaukuille"));
        wagon.smoking = nullIfFalse(node.get("tupakointi"));
        wagon.disabled = nullIfFalse(node.get("pyoratuolipaikka"));

        final JsonNode kalustoyksikko = node.get("kalustoyksikko");
        if (!isNodeNull(kalustoyksikko)) {
            final String sarjatunnus = kalustoyksikko.get("sarjatunnus").asText();
            wagon.wagonType = sarjatunnus;

            final JsonNode kalustoyksikkonroNode = kalustoyksikko.get("kalustoyksikkonro");
            if (!isNodeNull(kalustoyksikkonroNode) && StringUtils.containsAnyIgnoreCase(sarjatunnus, typesForWhichVehicleNumberPublishingIsAllowed)) {
                wagon.vehicleNumber = kalustoyksikkonroNode.asText();
            }
        }
        return wagon;
    }

    public Wagon transformToWagon(final OsavalinVaunuDto vaunu, final Integer trainNumber) {
        return new Wagon(
                vaunu.getSarjatunnus(), // final String wagonType / sarjatunnus is concluded from EVN
                vaunu.getJarjestysnumero(), // final int location
                vaunu.getKaupallinenNumero() != null ? Integer.parseInt(vaunu.getKaupallinenNumero()) : -1, // final int salesNumber
                vaunu.getPituus() != null ? vaunu.getPituus().intValue() / 10 : -1, // final int length (convert lenght mm->cm)
                nullIfFalse(vaunu.isPlayground()), // final Boolean playground,
                nullIfFalse(vaunu.isPet()), // final Boolean pet
                nullIfFalse(vaunu.isCatering()), // final Boolean catering
                nullIfFalse(vaunu.isVideo()), // final Boolean video,
                nullIfFalse(vaunu.isLuggage()), // final Boolean luggage
                nullIfFalse(vaunu.isSmoking()), // final Boolean smoking
                nullIfFalse(vaunu.isDisabled()), // final Boolean disabled
                getVehicleNumber(vaunu.getEurooppatunnus(), vaunu.getTunniste(), vaunu.getSarjatunnus(), trainNumber)); // vehicleNumber
    }

    public String getVehicleNumber(final String wagonEvn, final String wagonId, final String wagonType, final Integer trainNumber) {
        // There should not be need to check typesForWhichVehicleNumberPublishingIsAllowed as those values are already filtered but have values null or *****
        final String vehicleNumber =
                StringUtils.firstNonBlank(
                        StringUtils.replace(wagonEvn, "*", ""),
                        StringUtils.replace(wagonId, "*", "")
                );
        // But we still double check
        if (!StringUtils.equalsAnyIgnoreCase(wagonType, typesForWhichVehicleNumberPublishingIsAllowed)) {
            if (StringUtils.isNotBlank(vehicleNumber)) {
                log.warn("method=getVehicleNumber vehicleNumber={} not empty for not allowed wagonType={} evn={} wagonId={} trainNumber={} returning null",
                         vehicleNumber, wagonType, wagonEvn, wagonId, trainNumber);
            }
            return null;
        }

        if (StringUtils.isBlank(vehicleNumber)) {
            log.warn("method=getVehicleNumber vehicleNumber=null for wagonType={} evn={} wagonId={} trainNumber={}",
                     wagonType, wagonEvn, wagonId, trainNumber);
        } else if (!isEvn(vehicleNumber)) {
            log.warn("method=getVehicleNumber vehicleNumber={} not valid EVN for wagonType={} evn={} wagonId={} trainNumber={}",
                     vehicleNumber, wagonType, wagonEvn, wagonId, trainNumber);
        }
        return vehicleNumber;
    }

    public static boolean isEvn(final String wagonEvn) {
        return wagonEvn != null && EVN_PATTERN.matcher(wagonEvn).matches();
    }
}
