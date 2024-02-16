package fi.livi.rata.avoindata.updater.service.gtfs;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STATION;
import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STOP;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.locationtech.jts.geom.Point;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Platform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.PlatformData;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import jakarta.annotation.PostConstruct;

@Service
public class GTFSStopsService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private Map<String, double[]> customCoordinates = new HashMap<>();

    @PostConstruct
    private void setup() {
        // RU
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

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    public Map<String, Stop> createStops(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                         final List<SimpleTimeTableRow> timeTableRows,
                                         final PlatformData platformData) {
        final List<Stop> stops = new ArrayList<>();
        final Map<String, Set<String>> tracksScheduledByStation = new HashMap<>();

        timeTableRows.forEach(ttr -> {
            if (platformData.isValidTrack(ttr.stationShortCode, ttr.commercialTrack)) {
                tracksScheduledByStation.putIfAbsent(ttr.stationShortCode, new HashSet<>());
                tracksScheduledByStation.get(ttr.stationShortCode).add(ttr.commercialTrack);
            }
        });

        final Map<String, StationEmbeddable> uniqueStationEmbeddables = new HashMap<>();
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
            final String stationShortCode = stationEmbeddable.stationShortCode;
            final Station station = stationRepository.findByShortCode(stationShortCode);

            final Stop stationEntry = createStationStop(station, stationShortCode, LOCATION_TYPE_STATION);
            final Stop tracklessStop = createStationStop(station, stationShortCode, LOCATION_TYPE_STOP);

            stops.add(stationEntry);
            stops.add(tracklessStop);

            for (final String scheduledTrack : tracksScheduledByStation.getOrDefault(stationShortCode, Collections.emptySet())) {
                if (station != null) {
                    final Optional<InfraApiPlatform> infraApiPlatform = platformData.getStationPlatform(stationShortCode, scheduledTrack);
                    final Platform platformStop = createPlatformStop(station, infraApiPlatform, scheduledTrack);

                    stops.add(platformStop);
                }
            }
        }

        for (final Stop stop : stops) {
            stop.name = stop.name.replace(" asema", "");
        }

        return Maps.uniqueIndex(stops, s -> s.stopId);
    }

    private Stop createStationStop(final Station station, final String stationShortCode, final int locationType) {
        final Stop stop = new Stop(station);
        stop.stopId = locationType == LOCATION_TYPE_STOP ?
                      stationShortCode + "_0" :
                      stationShortCode;
        stop.stopCode = stationShortCode;
        stop.locationType = locationType;

        if (station != null) {
            stop.name = station.name.replace("_", " ");
            stop.latitude = station.latitude.doubleValue();
            stop.longitude = station.longitude.doubleValue();
        } else {
            stop.name = "-";
            log.warn("Could not find Station for {}", stationShortCode);
        }

        setCustomLocations(stationShortCode, stop);

        return stop;
    }

    private Platform createPlatformStop(final Station station, final Optional<InfraApiPlatform> infraApiPlatform, final String scheduledTrack) {
        final String stopId = station.shortCode + "_" + scheduledTrack;
        final String stopCode = station.shortCode;
        final String track = scheduledTrack;

        final String name = station.name.replace("_", " ");

        final Optional<Point> centroid = infraApiPlatform.map(platform -> platform.geometry.getCentroid());

        final double latitude = centroid.map(location -> location.getY()).orElseGet(() -> station.latitude.doubleValue());
        final double longitude = centroid.map(location -> location.getX()).orElseGet(() -> station.longitude.doubleValue());

        return new Platform(station, stopId, stopCode, name, latitude, longitude, track);
    }

    private double[] liviToWsgArray(final double p, final double i) {
        final ProjCoordinate wsg84 = this.wgs84ConversionService.liviToWgs84(i, p);
        return new double[] { wsg84.y, wsg84.x };
    }

    private void setCustomLocations(final String stationShortCode, final Stop stop) {
        final double[] coordinates = customCoordinates.get(stationShortCode);
        if (coordinates != null) {
            stop.latitude = coordinates[0];
            stop.longitude = coordinates[1];
        }
    }
}
