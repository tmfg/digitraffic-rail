package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

@Service
public class GTFSStopsService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private Map<String, double[]> customCoordinates = new HashMap<>();

    @PostConstruct
    private void setup() {
        customCoordinates.put("NRL", new double[]{62.174676, 30.603983});
        customCoordinates.put("BSL", new double[]{60.818538, 28.411931});
        customCoordinates.put("VYB", new double[]{60.715331, 28.751582});
        customCoordinates.put("PGO", new double[]{60.085222, 30.253509});
        customCoordinates.put("PTR", new double[]{59.955762, 30.356215});
        customCoordinates.put("TVE", new double[]{56.834918, 35.894602});
        customCoordinates.put("PTL", new double[]{59.932326, 30.439884});
        customCoordinates.put("BOLO", new double[]{57.877987, 34.106246});
        customCoordinates.put("MVA", new double[]{55.776115, 37.655077});
        customCoordinates.put("PRK", new double[]{61.783872, 34.344124});
    }

    @Autowired
    private StationRepository stationRepository;

    public Map<String, Stop> createStops(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                         final List<SimpleTimeTableRow> timeTableRows,
                                         final Map<String, Map<String, InfraApiPlatform>> platformsByStationAndTrack) {
        List<Stop> stops = new ArrayList<>();

        Map<String, Set<String>> tracksScheduledByStation = new HashMap<>();
        timeTableRows.forEach(ttr -> {
            if (platformsByStationAndTrack.getOrDefault(ttr.stationShortCode, Collections.emptyMap()).containsKey(ttr.commercialTrack)) {
                tracksScheduledByStation.putIfAbsent(ttr.stationShortCode, new HashSet<>());
                tracksScheduledByStation.get(ttr.stationShortCode).add(ttr.commercialTrack);
            }
        });

        Map<String, StationEmbeddable> uniqueStationEmbeddables = new HashMap<>();
        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);
                for (final ScheduleRow scheduleRow : schedule.scheduleRows) {
                    uniqueStationEmbeddables.putIfAbsent(scheduleRow.station.stationShortCode, scheduleRow.station);
                }
            }
        }

        for (final StationEmbeddable stationEmbeddable : uniqueStationEmbeddables.values()) {
            String stationShortCode = stationEmbeddable.stationShortCode;
            final Station station = stationRepository.findByShortCode(stationShortCode);

            Stop stop = new Stop(station);
            stop.stopId = stationShortCode;
            stop.stopCode = stationShortCode;

            if (station != null) {
                stop.name = station.name.replace("_", " ");
                stop.latitude = station.latitude.doubleValue();
                stop.longitude = station.longitude.doubleValue();
            } else {
                log.warn("Could not find Station for {}", stationShortCode);
            }

            setCustomLocations(stationShortCode, stop);

            stops.add(stop);

            for (String track : tracksScheduledByStation.getOrDefault(stationShortCode, Collections.emptySet())) {
                Stop stopWithTrack = new Stop(station);
                stopWithTrack.stopId = stationShortCode + "_" + track;
                stopWithTrack.stopCode = stationShortCode;
                stopWithTrack.track = track;

                stopWithTrack.name = station.name.replace("_", " ") + " raide " + track;

                if (platformsByStationAndTrack.get(stationShortCode).containsKey(track)) {
                    InfraApiPlatform platform = platformsByStationAndTrack.get(stationShortCode).get(track);

                    stopWithTrack.name = platform.description;

                    Point centroid = platform.geometry.getCentroid();
                    stopWithTrack.latitude = centroid.getY();
                    stopWithTrack.longitude = centroid.getX();

                } else {
                    if (station != null) {
                        stopWithTrack.latitude = station.latitude.doubleValue();
                        stopWithTrack.longitude = station.longitude.doubleValue();
                    } else {
                        log.warn("Could not find Station for {}", stationShortCode);
                    }
                }

                stops.add(stopWithTrack);
            }

        }

        return Maps.uniqueIndex(stops, s -> s.stopId);
    }

    private void setCustomLocations(String stationShortCode, Stop stop) {
        double[] coordinates = customCoordinates.get(stationShortCode);
        if (coordinates != null) {
            stop.latitude = coordinates[0];
            stop.longitude = coordinates[1];
        }
    }
}
