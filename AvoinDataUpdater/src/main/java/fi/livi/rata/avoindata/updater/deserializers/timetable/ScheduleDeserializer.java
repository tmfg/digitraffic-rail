package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.PreviousAndNext;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class ScheduleDeserializer extends AEntityDeserializer<Schedule> {

    @Override
    public Schedule deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final Schedule schedule = new Schedule();

        schedule.id = node.get("id").asLong();
        schedule.startDate = getNodeAsLocalDate(node.get("alkupvm"));
        schedule.endDate = getNodeAsLocalDate(node.get("loppupvm"));
        schedule.trainNumber = node.get("aikataulunJunanumero").get("junanumero").asLong();
        schedule.version = node.get("version").asLong();
        schedule.timetableType = node.get("kiireellinenHakemus") != null ? Train.TimetableType.ADHOC : Train.TimetableType.REGULAR;
        schedule.typeCode = node.get("tyyppi").asText();
        schedule.changeType = node.get("muutos").asText();
        schedule.capacityId = node.get("kapasiteettiId").asText();
        schedule.acceptanceDate = getNodeAsDateTime(node.get("hyvaksymisaika"));
        final JsonNode lahiliikenteenLinjatunnusNode = node.get("lahiliikenteenLinjatunnus");
        if (lahiliikenteenLinjatunnusNode != null) {
            schedule.commuterLineId = lahiliikenteenLinjatunnusNode.get("nimi").asText();
        }

        final Operator operator = new Operator();
        operator.operatorUICCode = node.get("operaattori").get("uicKoodi").asInt();
        operator.operatorShortCode = node.get("operaattori").get("lyhenne").asText();
        schedule.operator = operator;

        final TrainType trainType = new TrainType();
        trainType.name = node.get("junatyyppi").get("nimi").asText();
        trainType.id = node.get("junatyyppi").get("id").asLong();
        trainType.commercial = node.get("junatyyppi").get("kaupallinenJunatyyppi").asBoolean();
        schedule.trainType = trainType;

        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = node.get("junatyyppi").get("junalaji").get("nimi").asText();
        trainCategory.id = node.get("junatyyppi").get("junalaji").get("id").asLong();
        schedule.trainCategory = trainCategory;

        schedule.effectiveFrom = getNodeAsLocalDate(node.get("voimaanastumishetki"));
        schedule.runOnMonday = getNullableBoolean(node, "kulkuMa");
        schedule.runOnTuesday = getNullableBoolean(node, "kulkuTi");
        schedule.runOnWednesday = getNullableBoolean(node, "kulkuKe");
        schedule.runOnThursday = getNullableBoolean(node, "kulkuTo");
        schedule.runOnFriday = getNullableBoolean(node, "kulkuPe");
        schedule.runOnSaturday = getNullableBoolean(node, "kulkuLa");
        schedule.runOnSunday = getNullableBoolean(node, "kulkuSu");

        schedule.scheduleRows = Lists.newArrayList(
                jsonParser.getCodec().readValue(node.get("aikataulurivis").traverse(jsonParser.getCodec()), ScheduleRow[].class));

        schedule.scheduleRows.sort((o1, o2) -> Long.compare(o1.id, o2.id));

        for (final ScheduleRow scheduleRow : schedule.scheduleRows) {
            scheduleRow.schedule = schedule;
        }

        schedule.scheduleCancellations = Sets.newHashSet(
                jsonParser.getCodec().readValue(node.get("peruminens").traverse(jsonParser.getCodec()), ScheduleCancellation[].class));

        schedule.scheduleExceptions = Sets.newHashSet(
                jsonParser.getCodec().readValue(node.get("poikkeuspaivas").traverse(jsonParser.getCodec()), ScheduleException[].class));


        improveLiikeCancellationLogic(schedule);

        return schedule;
    }

    private void improveLiikeCancellationLogic(final Schedule schedule) {
        for (final ScheduleCancellation scheduleCancellation : schedule.scheduleCancellations) {
            if (scheduleCancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.PARTIALLY ||
                    scheduleCancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE) {
                for (final PreviousAndNext<ScheduleRow> previousAndNext : PreviousAndNext.build(schedule.scheduleRows)) {

                    final Set<ScheduleRowPart> cancelledRowParts = scheduleCancellation.cancelledRows;
                    if (previousAndNext.previous != null) {
                        //Viimeinen lähtö peruttu, mutta nykyinen saapuminen ei => Perutaan lisää
                        if (cancelledRowParts.contains(previousAndNext.previous.departure) && !cancelledRowParts.contains(
                                previousAndNext.current.arrival)) {
                            cancelledRowParts.add(previousAndNext.current.arrival);
                        }

                        //Viimeinen lähtö ei ole peruttu, mutta nykyinen saapuminen on => Perutaan vähemmän
                        if (!cancelledRowParts.contains(previousAndNext.previous.departure) && cancelledRowParts.contains(
                                previousAndNext.current.arrival)) {
                            cancelledRowParts.remove(previousAndNext.current.arrival);
                        }
                    }
                }
            }
        }
    }
}
