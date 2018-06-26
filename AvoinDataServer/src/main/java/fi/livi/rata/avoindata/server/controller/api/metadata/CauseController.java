package fi.livi.rata.avoindata.server.controller.api.metadata;


import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.dao.cause.CategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.DetailedCategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.ThirdCategoryCodeRepository;
import fi.livi.rata.avoindata.common.domain.cause.*;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;

@RestController
public class CauseController extends AMetadataController {
    private Logger log = LoggerFactory.getLogger(CauseController.class);

    @Autowired
    private DetailedCategoryCodeRepository detailedCategoryCodeRepository;

    @Autowired
    private CategoryCodeRepository categoryCodeRepository;

    @Autowired
    private ThirdCategoryCodeRepository thirdCategoryCodeRepository;

    private HashMap<String, PassengerTerm> translations;

    @JsonView(CategoryCodeJsonView.All.class)
    @ApiOperation("Returns list of cause category codes")
    @RequestMapping(value = "cause-category-codes", method = RequestMethod.GET)
    @ResponseBody
    public List<CategoryCode> getCauseCodes(@RequestParam(defaultValue = "false", name = "show_inactive") final boolean showInactive,
            HttpServletResponse response) {
        final List<CategoryCode> output;
        if (showInactive) {
            output = categoryCodeRepository.findAll();
        } else {
            output = categoryCodeRepository.findActiveCategoryCodes();
        }

        fillCauseTranslations(output);

        setCache(response, output);

        return output;
    }

    @JsonView(CategoryCodeJsonView.All.class)
    @ApiOperation("Returns list of detailed cause category codes")
    @RequestMapping(value = "detailed-cause-category-codes", method = RequestMethod.GET)
    @ResponseBody
    public List<DetailedCategoryCode> getDetailedCauseResources(
            @RequestParam(defaultValue = "false", name = "show_inactive") final boolean showInactive, HttpServletResponse response) {
        final List<DetailedCategoryCode> output;
        if (showInactive) {
            output = detailedCategoryCodeRepository.findAll();
        } else {
            output = detailedCategoryCodeRepository.findActiveDetailedCategoryCodes();
        }

        fillCauseTranslations(output);

        CacheConfig.METADATA_CACHECONTROL.setCacheParameter(response, output);

        return output;
    }

    @ApiOperation("Returns list of third cause category codes")
    @JsonView(CategoryCodeJsonView.All.class)
    @RequestMapping(value = "third-cause-category-codes", method = RequestMethod.GET)
    @ResponseBody
    public List<ThirdCategoryCode> getThirdCauseResources(
            @RequestParam(defaultValue = "false", name = "show_inactive") final boolean showInactive, HttpServletResponse response) {
        final List<ThirdCategoryCode> output;
        if (showInactive) {
            output = thirdCategoryCodeRepository.findAll();
        } else {
            output = thirdCategoryCodeRepository.findActiveDetailedCategoryCodes();
        }

        fillCauseTranslations(output);

        CacheConfig.METADATA_CACHECONTROL.setCacheParameter(response, output);

        return output;
    }

    private void fillCauseTranslations(List<? extends ACauseCode> causeCodes) {
        for (final ACauseCode causeCode : causeCodes) {
            final String idString = causeCode.getIdString();
            final PassengerTerm passengerTerm = translations.get(idString);
            if (passengerTerm != null) {
                causeCode.passengerTerm = passengerTerm;
            } else {
                log.debug("Could not find translation for {}", idString);
                causeCode.passengerTerm = null;
            }
        }
    }

    @PostConstruct
    private void setup() {
        this.translations = new HashMap<String, PassengerTerm>();

        final PassengerTerm DELAYED_DEPARTURE_PREPARATIONS = new PassengerTerm("Viivästyneet lähtövalmistelut",
                "Försenade avgångsförberedelser", "Delayed departure preparations");
        final PassengerTerm TRAIN_WAITING_FOR_ROLLING_STOCK = new PassengerTerm("Kaluston odotus", "Väntan på materiel",
                "Train waiting for rolling stock");
        final PassengerTerm WEATHER_CONDITIONS = new PassengerTerm("Sääolosuhteet", "Väderförhållandena",
                "The weather conditions");
        final PassengerTerm VANDALISM = new PassengerTerm("Ilkivalta", "Skadegörelse", "Vandalism");
        final PassengerTerm TECHNICAL_FAULT_IN_THE_TRAIN = new PassengerTerm("Junan tekninen vika",
                "Ett tekniskt fel i tåget", "A technical fault in the train");
        final PassengerTerm LOWERED_SPEED_LIMIT = new PassengerTerm("Alennettu nopeusrajoitus", "Sänkt hastighetsbegränsning",
                "A lowered speed limit ");
        final PassengerTerm CONNECTION_SERVICES = new PassengerTerm("Yhteysliikenteen odotus", "Väntan på anslutande trafik",
                "The train waiting for connecting services");
        final PassengerTerm OTHER_TRAIN_SERVICES = new PassengerTerm("Muu junaliikenne", "Annan tågtrafik",
                "Other train services");
        final PassengerTerm EXTENDED_STOP = new PassengerTerm("Pitkittynyt pysähdys", "Ett förlängt stopp",
                "An extended stop");
        final PassengerTerm PERSONAL_INJURY = new PassengerTerm("Henkilövahinko", "En personskada", "A personal injury");
        final PassengerTerm ANIMAL_COLLISION = new PassengerTerm("Eläimen allejäänti", "En djurolycka",
                "An animal collision");
        final PassengerTerm LEVEL_CROSSING_ACCIDENT = new PassengerTerm("Tasoristeysonnettomuus", "En plankorsningsolycka",
                "A level crossing accident");
        final PassengerTerm ACCIDENT = new PassengerTerm("Onnettomuus", "En olycka", "An accident");
        final PassengerTerm FAULT_IN_THE_RAILWAY_SYSTEM = new PassengerTerm("Ratajärjestelmävika", "Fel i järnvägssystemet",
                "A fault in the railway system");
        final PassengerTerm FAULT_IN_THE_DATA_SYSTEM = new PassengerTerm("Tietojärjestelmävika", "Fel i datasystemet",
                "A fault in the data system");
        final PassengerTerm OBSTRUCTION_ON_THE_TRACK = new PassengerTerm("Radalla este", "Ett hinder på spåret",
                "An obstruction on the track");
        final PassengerTerm TRACK_WORK = new PassengerTerm("Ratatyöt", "Banarbeten", "Track work");
        final PassengerTerm ELECTRICAL_FAULT = new PassengerTerm("Sähkövika", "Ett elektriskt fel", "An electrical fault");
        final PassengerTerm BROKEN_TRAIN_ON_THE_TRACK = new PassengerTerm("Radalle rikkoutunut juna", "Tågfel på banan",
                "A broken train on the track");

        this.translations.put("H_21", DELAYED_DEPARTURE_PREPARATIONS);

        this.translations.put("H1_81", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H2_82", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H3_83", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("I1_125", WEATHER_CONDITIONS);
        this.translations.put("I2_126", WEATHER_CONDITIONS);
        this.translations.put("I3_127", VANDALISM);
        //        this.translations.put("I4_128", UNKNOWN);
        this.translations.put("J1_84", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("K1_85", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K2_86", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K3_87", LOWERED_SPEED_LIMIT);
        this.translations.put("K4_88", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K5_89", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K6_90", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("L1_100", CONNECTION_SERVICES);
        this.translations.put("L2_101", OTHER_TRAIN_SERVICES);
        this.translations.put("L3_102", OTHER_TRAIN_SERVICES);
        this.translations.put("L4_103", OTHER_TRAIN_SERVICES);
        this.translations.put("L5_104", BROKEN_TRAIN_ON_THE_TRACK);
        this.translations.put("L6_105", OTHER_TRAIN_SERVICES);
        this.translations.put("L7_106", OTHER_TRAIN_SERVICES);
        this.translations.put("M1_123", EXTENDED_STOP);
        this.translations.put("M2_124", EXTENDED_STOP);
        this.translations.put("O1_119", PERSONAL_INJURY);
        this.translations.put("O2_120", ANIMAL_COLLISION);
        this.translations.put("O3_121", LEVEL_CROSSING_ACCIDENT);
        this.translations.put("O4_122", ACCIDENT);
        this.translations.put("P1_107", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P2_108", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P3_109", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P4_110", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("R1_116", TRACK_WORK);
        this.translations.put("R2_117", TRACK_WORK);
        this.translations.put("R3_118", TRACK_WORK);
        this.translations.put("S1_111", ELECTRICAL_FAULT);
        this.translations.put("S2_112", ELECTRICAL_FAULT);
        this.translations.put("T1_113", LOWERED_SPEED_LIMIT);
        this.translations.put("T2_114", LOWERED_SPEED_LIMIT);
        this.translations.put("T3_115", OBSTRUCTION_ON_THE_TRACK);
        this.translations.put("V1_91", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V2_92", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V3_93", LOWERED_SPEED_LIMIT);
        this.translations.put("V4_94", DELAYED_DEPARTURE_PREPARATIONS);

        this.translations.put("H101_1", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H102_2", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H103_3", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H104_4", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H105_5", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("H106_6", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("I301_183", VANDALISM);
        this.translations.put("I302_184", VANDALISM);
        this.translations.put("J101_9", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J102_10", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J103_11", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J104_12", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J105_13", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J106_14", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J107_15", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("J108_16", DELAYED_DEPARTURE_PREPARATIONS);
        this.translations.put("K101_17", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K102_18", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K103_19", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K104_20", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K105_21", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("K201_22", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K202_23", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K203_24", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K204_25", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K205_26", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K206_27", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K207_28", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K208_29", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K209_30", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K210_31", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K211_32", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K212_33", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K213_34", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K301_35", LOWERED_SPEED_LIMIT);
        this.translations.put("K302_36", LOWERED_SPEED_LIMIT);
        this.translations.put("K303_37", LOWERED_SPEED_LIMIT);
        this.translations.put("K304_38", LOWERED_SPEED_LIMIT);
        this.translations.put("K305_39", LOWERED_SPEED_LIMIT);
        this.translations.put("K306_40", LOWERED_SPEED_LIMIT);
        this.translations.put("K401_41", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K402_42", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K403_43", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K501_44", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("K502_45", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("L101_88", CONNECTION_SERVICES);
        this.translations.put("L102_89", CONNECTION_SERVICES);
        this.translations.put("L201_90", OTHER_TRAIN_SERVICES);
        this.translations.put("L202_91", OTHER_TRAIN_SERVICES);
        this.translations.put("L203_92", OTHER_TRAIN_SERVICES);
        this.translations.put("L204_93", OTHER_TRAIN_SERVICES);
        this.translations.put("L301_94", OTHER_TRAIN_SERVICES);
        this.translations.put("L302_95", OTHER_TRAIN_SERVICES);
        this.translations.put("L303_96", OTHER_TRAIN_SERVICES);
        this.translations.put("L304_97", OTHER_TRAIN_SERVICES);
        this.translations.put("L501_98", BROKEN_TRAIN_ON_THE_TRACK);
        this.translations.put("L502_99", BROKEN_TRAIN_ON_THE_TRACK);
        this.translations.put("L503_100", BROKEN_TRAIN_ON_THE_TRACK);
        this.translations.put("L504_101", BROKEN_TRAIN_ON_THE_TRACK);
        this.translations.put("L601_102", OTHER_TRAIN_SERVICES);
        this.translations.put("L602_103", OTHER_TRAIN_SERVICES);
        this.translations.put("L701_104", OTHER_TRAIN_SERVICES);
        this.translations.put("L702_105", OTHER_TRAIN_SERVICES);
        this.translations.put("L703_106", OTHER_TRAIN_SERVICES);
        this.translations.put("L704_107", OTHER_TRAIN_SERVICES);
        this.translations.put("M101_173", EXTENDED_STOP);
        this.translations.put("M102_174", EXTENDED_STOP);
        this.translations.put("M103_175", EXTENDED_STOP);
        this.translations.put("M104_176", EXTENDED_STOP);
        this.translations.put("M105_177", EXTENDED_STOP);
        this.translations.put("M106_178", EXTENDED_STOP);
        this.translations.put("M107_179", EXTENDED_STOP);
        this.translations.put("M108_180", EXTENDED_STOP);
        this.translations.put("M109_181", EXTENDED_STOP);
        this.translations.put("M110_182", EXTENDED_STOP);
        this.translations.put("O401_168", ACCIDENT);
        this.translations.put("O402_169", ACCIDENT);
        this.translations.put("O403_170", ACCIDENT);
        this.translations.put("O404_171", ACCIDENT);
        this.translations.put("O405_172", ACCIDENT);
        this.translations.put("P101_108", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P102_109", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P103_110", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P104_111", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P105_112", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P106_113", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P107_114", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P108_115", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P109_116", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P110_117", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P111_118", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P112_119", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P113_120", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P114_121", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P115_122", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P116_123", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P117_124", FAULT_IN_THE_RAILWAY_SYSTEM);
        this.translations.put("P201_125", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P202_126", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P203_127", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P204_128", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P205_129", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P206_130", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P207_131", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P208_132", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P209_133", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P301_134", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P302_135", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P303_136", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P304_137", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P401_138", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P402_139", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("P403_140", FAULT_IN_THE_DATA_SYSTEM);
        this.translations.put("R201_160", TRACK_WORK);
        this.translations.put("R202_161", TRACK_WORK);
        this.translations.put("R203_162", TRACK_WORK);
        this.translations.put("R204_163", TRACK_WORK);
        this.translations.put("R205_164", TRACK_WORK);
        this.translations.put("R206_165", TRACK_WORK);
        this.translations.put("R301_166", TRACK_WORK);
        this.translations.put("R302_167", TRACK_WORK);
        this.translations.put("S101_141", ELECTRICAL_FAULT);
        this.translations.put("S102_142", ELECTRICAL_FAULT);
        this.translations.put("S103_143", ELECTRICAL_FAULT);
        this.translations.put("S104_144", ELECTRICAL_FAULT);
        this.translations.put("S201_145", ELECTRICAL_FAULT);
        this.translations.put("S202_146", ELECTRICAL_FAULT);
        this.translations.put("S203_147", ELECTRICAL_FAULT);
        this.translations.put("S204_148", ELECTRICAL_FAULT);
        this.translations.put("S205_149", ELECTRICAL_FAULT);
        this.translations.put("S206_150", ELECTRICAL_FAULT);
        this.translations.put("T101_155", LOWERED_SPEED_LIMIT);
        this.translations.put("T102_156", LOWERED_SPEED_LIMIT);
        this.translations.put("T301_157", OBSTRUCTION_ON_THE_TRACK);
        this.translations.put("T302_158", OBSTRUCTION_ON_THE_TRACK);
        this.translations.put("T303_159", OBSTRUCTION_ON_THE_TRACK);
        this.translations.put("V101_46", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V102_47", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V103_48", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V104_49", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V105_50", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V106_51", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V107_52", TRAIN_WAITING_FOR_ROLLING_STOCK);
        this.translations.put("V201_53", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V202_54", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V203_55", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V204_56", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V205_57", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V206_58", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V207_59", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V208_60", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V209_61", TECHNICAL_FAULT_IN_THE_TRAIN);
        this.translations.put("V301_62", LOWERED_SPEED_LIMIT);
        this.translations.put("V302_63", LOWERED_SPEED_LIMIT);
        this.translations.put("V303_64", LOWERED_SPEED_LIMIT);
        this.translations.put("V304_65", LOWERED_SPEED_LIMIT);
        this.translations.put("V305_66", LOWERED_SPEED_LIMIT);
        this.translations.put("V306_67", LOWERED_SPEED_LIMIT);

    }
}
