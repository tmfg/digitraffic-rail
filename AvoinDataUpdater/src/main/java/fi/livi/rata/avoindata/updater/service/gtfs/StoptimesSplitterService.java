package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

// Splits GTFS-trips into smaller sections so that geometries can be successfully fetched from infra-api. Infra-api does not like long distances or Y-shaped routes
@Service
public class StoptimesSplitterService {
    private static class SplittingLogic {
        public String stationToSplit;
        public List<String> stationsThatMustBePresent;

        public SplittingLogic(String stationToSplit, List<String> stationsThatMustBePresent) {
            this.stationToSplit = stationToSplit;
            this.stationsThatMustBePresent = stationsThatMustBePresent;
        }
    }

    private Map<String, List<SplittingLogic>> splittingLogics = new HashMap<>();

    @PostConstruct
    public void setup() {
        this.createSplittingLogic(List.of("JPA", "KÖK", "VT"));     // Seinäjoki -> Kokkola                 Distance
        this.createSplittingLogic(List.of("MD", "RLÄ", "KIS"));     // Tampere -> Seinäjoki                 Distance
        this.createSplittingLogic(List.of("KÖ", "LM", "HP"));       // Mynämäki -> Tampere                  Distance
        this.createSplittingLogic(List.of("KNS", "ELA", "SVI"));    // Kokkola -> Ylivieska (RR?)           Distance
        this.createSplittingLogic(List.of("SR", "POI", "KTI"));     // Parikkala -> Säkäniemi (UIM-KTT?)    Distance
        this.createSplittingLogic(List.of("KII", "MR", "MYT"));     // Kouvola -> Mynttilä (TA-OT?)         Distance
        this.createSplittingLogic(List.of("KPA", "KJR", "TA"));     // Kouvola -> Luumäki (HMA-?)           Distance
        this.createSplittingLogic(List.of("UKÄ", "MKA", "KA"));     // Lahti -> Kouvola (SLD-VNAT?)         Distance
        this.createSplittingLogic(List.of("HPS", "MRK", "SNJ"));    // Pieksämäki -> Kuopio (MKA-OL?, KMM)  Distance
        this.createSplittingLogic(List.of("PTO", "MLL", "KVJ"));    // Kontiomäki -> Oulu                   Distance
        this.createSplittingLogic(List.of("TV", "LÄP", "LPR"));     // Tampere -> Jyväskylä                 Distance
        this.createSplittingLogic(List.of("PRL", "LTS", "ITA"));    // Hämeenlinna -> Tampere               Distance
        this.createSplittingLogic(List.of("KLO", "HPK", "KEU"));    // Kolho -> Keuruu                      Y-shaped
        this.createSplittingLogic(List.of("AHO", "TJA", "RHE"));    // Aho -> Raahe (Rautaruukki)           Y-shaped
        this.createSplittingLogic(List.of("KUK", "KVLA", "HJ"));    // Kuusankosi -> Harju                  Y-shaped Duplicate?
        this.createSplittingLogic(List.of("HJ", "KV", "KUK"));      // Kuusankosi -> Harju                  Y-shaped Duplicate?
        this.createSplittingLogic(List.of("KUK", "KVLA", "KRA"));   // Kuusankosi -> Koria                  Y-shaped
        this.createSplittingLogic(List.of("KRA", "KV", "HJ"));      // Koria -> Harju                       Y-shaped
        this.createSplittingLogic(List.of("RHL", "JY", "VRI"));     // Vaajakoski -> Vihtavuori             Y-shaped
        this.createSplittingLogic(List.of("RAS", "LÄ", "PL"));      // Rasinsuo -> Pulsa (VNA)              Y-shaped
        this.createSplittingLogic(List.of("VLH", "LH", "OM"));      // Villilähde -> Orimattila             Y-shaped
        this.createSplittingLogic(List.of("VSA", "KE", "SAV"));     // Vuosaari -> Kerava                   Y-shaped
        this.createSplittingLogic(List.of("KU", "TL", "UR"));       // Kuurila -> Urjala                    Y-shaped
        this.createSplittingLogic(List.of("HMA", "JRI", "TSL"));    // Hamina -> Kymi                       Y-shaped
        this.createSplittingLogic(List.of("APT", "LNA", "TE"));     // Iisalmi -> Siilinjärvi               Y-shaped
        this.createSplittingLogic(List.of("KRV", "PHÄ", "PYK"));    // Kiuruvesi -> Pyhäkumpu               Y-shaped
        this.createSplittingLogic(List.of("SKÄ", "PM", "TMU"));     // Siikamäki -> PM -> Temu              Y-shaped
        this.createSplittingLogic(List.of("RNN", "ILM", "SOA"));    // Runni -> Soininlahti (Iisalmi)       Y-shaped
        this.createSplittingLogic(List.of("VKS", "VEH", "KTÖ"));    // Lentokenttärata                      Correct route
        this.createSplittingLogic(List.of("HVK", "ASO", "LNÄ"));    // Lentokenttärata                      Correct route
        this.createSplittingLogic(List.of("SJ", "TPE", "JVS"));     // Changes direction @ TPE              Correct route
        this.createSplittingLogic(List.of("VNAR", "VNA", "VNAT"));  // Russia                               Correct route
    }

    public List<StopTime> splitStoptimes(Trip trip) {
        List<StopTime> actualStops = new ArrayList<>();
        StopTime firstStop = trip.stopTimes.get(0);
        StopTime lastStop = trip.stopTimes.get(trip.stopTimes.size() - 1);

        if (!this.shouldSplit(trip, firstStop)) {
            actualStops.add(firstStop);
        }

        for (int i = 0; i < trip.stopTimes.size(); i++) {
            StopTime current = trip.stopTimes.get(i);

            if (this.shouldSplit(trip, current)) {
                actualStops.add(current);
            }
        }

        if (!this.shouldSplit(trip, lastStop)) {
            actualStops.add(lastStop);
        }

        return actualStops;
    }

    private boolean shouldSplit(Trip trip, StopTime stopTime) {
        List<SplittingLogic> splittingLogic = this.splittingLogics.get(stopTime.stopId);
        if (splittingLogic != null) {
            List<Integer> indexes = new ArrayList<>();

            SplittingLogic matchingLogic = getMatchingSplittingLogic(trip, splittingLogic, indexes);

            if (matchingLogic == null) {
                return false;
            }

            if (!this.areIndexesInOrder(indexes, true) && !this.areIndexesInOrder(indexes, false)) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    private SplittingLogic getMatchingSplittingLogic(Trip trip, List<SplittingLogic> candidates, List<Integer> indexes) {
        SplittingLogic matchingLogic = null;

        for (SplittingLogic logic : candidates) {
            indexes.clear();
            for (String stationThatMustBePresent : logic.stationsThatMustBePresent) {
                Optional<Integer> indexOfStop = this.getIndexOfStop(trip.stopTimes, stationThatMustBePresent);
                if (indexOfStop.isPresent() && indexes.stream().filter(s -> s == indexOfStop.get()).findFirst().isEmpty()) {
                    indexes.add(indexOfStop.get());
                    matchingLogic = logic;
                } else {
                    matchingLogic = null;
                    break;
                }
            }

            if (matchingLogic != null) {
                return matchingLogic;
            }
        }
        return matchingLogic;
    }

    private boolean areIndexesInOrder(List<Integer> indexes, boolean ascending) {
        for (int i = 0; i < indexes.size() - 1; i++) {
            int current = indexes.get(i);
            int next = indexes.get(i + 1);

            if (ascending) {
                if (current > next) {
                    return false;
                }
            } else {
                if (current < next) {
                    return false;
                }
            }
        }

        return true;
    }

    private Optional<Integer> getIndexOfStop(List<StopTime> stopTimes, String stopId) {
        for (int i = 0; i < stopTimes.size(); i++) {
            StopTime stopTime = stopTimes.get(i);
            if (stopTime.stopId.equals(stopId)) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    private void createSplittingLogic(List<String> stationsThatMustBePresent) {
        String stationToSplitBy = stationsThatMustBePresent.get(1);
        List<SplittingLogic> splittingLogics = this.splittingLogics.get(stationToSplitBy);
        if (splittingLogics == null) {
            this.splittingLogics.put(stationToSplitBy, Lists.newArrayList(new SplittingLogic(stationToSplitBy, stationsThatMustBePresent)));
        } else {
            splittingLogics.add(new SplittingLogic(stationToSplitBy, stationsThatMustBePresent));
        }
    }
}
