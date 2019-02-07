package fi.livi.rata.avoindata.updater.config;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
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
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import fi.livi.rata.avoindata.updater.deserializers.*;
import fi.livi.rata.avoindata.updater.deserializers.timetable.*;
import fi.livi.rata.avoindata.updater.service.timetable.entities.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Component
@XRayEnabled
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
    private CategoryCodeDeserializer categoryCodeDeserializer;

    @Autowired
    private DetailedCategoryCodeDeserializer detailedCategoryCodeDeserializer;

    @Autowired
    private ThirdCategoryCodeDeserializer thirdCategoryCodeDeserializer;

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

        module.addDeserializer(CategoryCode.class, categoryCodeDeserializer);
        module.addDeserializer(DetailedCategoryCode.class, detailedCategoryCodeDeserializer);
        module.addDeserializer(ThirdCategoryCode.class, thirdCategoryCodeDeserializer);

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
