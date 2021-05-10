package fi.livi.rata.avoindata.updater.config;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import javax.annotation.PostConstruct;

import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.updater.deserializers.*;

import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.localization.Localizations;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;
import fi.livi.rata.avoindata.common.domain.trackwork.ElementRange;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import fi.livi.rata.avoindata.updater.deserializers.CauseDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.ElementRangeDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.ForecastDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.IdentifierRangeDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.JourneyCompositionDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.LocalizationsDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.LocomotiveDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.OperatorDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.OperatorTrainNumberDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.RoutesectionDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.RoutesetDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.RumaLocationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.StationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TimeTableRowDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrackRangeDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrackSectionDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrackWorkNotificationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrackWorkPartDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrafficRestrictionNotificationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainCategoryDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainLocationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainRunningMessageDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainRunningMessageRuleDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.TrainTypeDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.WagonDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.ScheduleCancellationDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.ScheduleDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.ScheduleExceptionDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.ScheduleRowDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.ScheduleRowPartDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.TimeTablePeriodChangeDateDeserializer;
import fi.livi.rata.avoindata.updater.deserializers.timetable.TimeTablePeriodDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

@Component
public class HttpInputObjectMapper extends ObjectMapper {
    public static final DateTimeFormatter ISO_FIXED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(
            ZoneId.of("Z"));

    @Autowired
    private StationDeserializer stationDeserializer;

    @Autowired
    private TrainRunningMessageDeserializer trainRunningMessageDeserializer;

    @Autowired
    private TrackSectionDeserializer trackSectionDeserializer;

    @Autowired
    private TrackRangeDeserializer trackRangeDeserializer;

    @Autowired
    private LocalizationsDeserializer localizationsDeserializer;

    @Autowired
    private TrainRunningMessageRuleDeserializer timeTableRowActivationDeserializer;

    @Autowired
    private TrainCategoryDeserializer trainCategoryDeserializer;

    @Autowired
    private TrainTypeDeserializer trainTypeDeserializer;

    @Autowired
    private TrainDeserializer trainDeserializer;

    @Autowired
    private TimeTableRowDeserializer timeTableRowDeserializer;

    @Autowired
    private CauseDeserializer causeDeserializer;

    @Autowired
    private JourneyCompositionDeserializer journeyCompositionDeserializer;

    @Autowired
    private WagonDeserializer wagonDeserializer;

    @Autowired
    private LocomotiveDeserializer locomotiveDeserializer;

    @Autowired
    private OperatorTrainNumberDeserializer operatorTrainNumberDeserializer;

    @Autowired
    private OperatorDeserializer operatorDeserializer;

    @Autowired
    private RoutesetDeserializer routesetDeserializer;

    @Autowired
    private RoutesectionDeserializer routesectionDeserializer;

    @Autowired
    private ForecastDeserializer forecastDeserializer;

    @Autowired
    private ScheduleDeserializer scheduleDeserializer;

    @Autowired
    private ScheduleRowDeserializer scheduleRowDeserializer;

    @Autowired
    private ScheduleCancellationDeserializer scheduleCancellationDeserializer;

    @Autowired
    private ScheduleRowPartDeserializer scheduleRowPartDeserializer;

    @Autowired
    private ScheduleExceptionDeserializer scheduleExceptionDeserializer;

    @Autowired
    private TrainLocationDeserializer trainLocationDeserializer;

    @Autowired
    private TimeTablePeriodDeserializer timetablePeriodDeserializer;

    @Autowired
    private TimeTablePeriodChangeDateDeserializer timeTablePeriodChangeDateDeserializer;

    @Autowired
    private TrackWorkNotificationDeserializer trackWorkNotificationDeserializer;

    @Autowired
    private TrafficRestrictionNotificationDeserializer trafficRestrictionNotificationDeserializer;

    @Autowired
    private TrackWorkPartDeserializer trackWorkPartDeserializer;

    @Autowired
    private RumaLocationDeserializer rumaLocationDeserializer;

    @Autowired
    private IdentifierRangeDeserializer identifierRangeDeserializer;

    @Autowired
    private ElementRangeDeserializer elementRangeDeserializer;

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        setSerializationInclusion(JsonInclude.Include.NON_NULL);
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);

        registerModule(new Jdk8Module());
        registerModule(new JavaTimeModule());
        registerModule(new JtsModule());

        final SimpleModule module = new SimpleModule();
        module.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer(ISO_FIXED_FORMAT));
        addTrainDeserializers(module);
        addCompositionDeserializers(module);
        addLocalizationDeserializers(module);
        addTrainRunningMessageDeserializers(module);

        module.addDeserializer(Station.class, stationDeserializer);
        module.addDeserializer(Operator.class, operatorDeserializer);
        module.addDeserializer(OperatorTrainNumber.class, operatorTrainNumberDeserializer);

        module.addDeserializer(Routeset.class, routesetDeserializer);
        module.addDeserializer(Routesection.class, routesectionDeserializer);

        module.addDeserializer(Forecast.class, forecastDeserializer);

        module.addDeserializer(Schedule.class, scheduleDeserializer);
        module.addDeserializer(ScheduleCancellation.class, scheduleCancellationDeserializer);
        module.addDeserializer(ScheduleRow.class, scheduleRowDeserializer);
        module.addDeserializer(ScheduleRowPart.class, scheduleRowPartDeserializer);
        module.addDeserializer(ScheduleException.class, scheduleExceptionDeserializer);

        module.addDeserializer(TrainLocation.class, trainLocationDeserializer);

        module.addDeserializer(TimeTablePeriod.class, timetablePeriodDeserializer);
        module.addDeserializer(TimeTablePeriodChangeDate.class, timeTablePeriodChangeDateDeserializer);

        module.addDeserializer(TrackWorkNotification.class, trackWorkNotificationDeserializer);
        module.addDeserializer(TrafficRestrictionNotification.class, trafficRestrictionNotificationDeserializer);
        module.addDeserializer(TrackWorkPart.class, trackWorkPartDeserializer);
        module.addDeserializer(RumaLocation.class, rumaLocationDeserializer);
        module.addDeserializer(IdentifierRange.class, identifierRangeDeserializer);
        module.addDeserializer(ElementRange.class, elementRangeDeserializer);

        registerModule(module);
    }

    private void addTrainRunningMessageDeserializers(final SimpleModule module) {
        module.addDeserializer(TrainRunningMessage.class, trainRunningMessageDeserializer);
        module.addDeserializer(TrackSection.class, trackSectionDeserializer);
        module.addDeserializer(TrackRange.class, trackRangeDeserializer);
        module.addDeserializer(TrainRunningMessageRule.class, timeTableRowActivationDeserializer);
    }

    private void addLocalizationDeserializers(final SimpleModule module) {
        module.addDeserializer(Localizations.class, localizationsDeserializer);
        module.addDeserializer(TrainCategory.class, trainCategoryDeserializer);
        module.addDeserializer(TrainType.class, trainTypeDeserializer);
    }

    private void addTrainDeserializers(final SimpleModule module) {
        module.addDeserializer(Train.class, trainDeserializer);
        module.addDeserializer(TimeTableRow.class, timeTableRowDeserializer);
        module.addDeserializer(Cause.class, causeDeserializer);
    }

    private void addCompositionDeserializers(final SimpleModule module) {
        module.addDeserializer(JourneyComposition.class, journeyCompositionDeserializer);
        module.addDeserializer(Wagon.class, wagonDeserializer);
        module.addDeserializer(Locomotive.class, locomotiveDeserializer);
    }
}
