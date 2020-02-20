package fi.livi.rata.avoindata.updater.updaters;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainLocationFilter;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainLocationNearTrackFilterService;

@Service
public class TrainLocationUpdater {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Autowired
    private RecentlySeenTrainLocationFilter recentlySeenTrainLocationFilter;

    @Autowired
    private DateProvider dateProvider;

    @Autowired
    private TrainLocationNearTrackFilterService trainLocationNearTrackFilterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${updater.liikeinterface-url}")
    private String liikeinterfaceUrl;

    @Value("${updater.kupla-enabled:true}")
    private boolean isKuplaEnabled;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private LastUpdateService lastUpdateService;

    private static final DecimalFormat IP_LOCATION_FILTER_PRECISION = new DecimalFormat("#.000000");

    //@Scheduled(fixedDelay = 1000)
    @Transactional
    public synchronized void trainLocation() {
            try {
                if (!Strings.isNullOrEmpty(liikeinterfaceUrl) && isKuplaEnabled) {
                    final ZonedDateTime start = dateProvider.nowInHelsinki();

                    List<TrainLocation> trainLocations = Arrays.asList(
                            objectMapper.readValue(new URL(liikeinterfaceUrl + "/kuplas"), TrainLocation[].class));

                    final List<TrainLocation> filteredTrainLocations = filterTrains(trainLocations);

                    try {
                        mqttPublishService.publish(
                                s -> String.format("train-locations/%s/%s", s.trainLocationId.departureDate, s.trainLocationId.trainNumber),
                                filteredTrainLocations, null);
                    } catch (Exception e) {
                        log.error("MQTT updated failed. Still trying to update database.", e);
                    }

                    trainLocationRepository.persist(filteredTrainLocations);

                    final ZonedDateTime end = dateProvider.nowInHelsinki();

                    log.info("Updated data for {} trainLocations (total received {}) in {} ms", filteredTrainLocations.size(),
                            trainLocations.size(), end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());

                    lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS);
                }
            } catch (Exception e) {
                log.error("Error updating train locations", e);
            }
    }

    private List<TrainLocation> filterTrains(final List<TrainLocation> trainLocations) {
        final List<TrainLocation> recentlySeenTrackLocations = recentlySeenTrainLocationFilter.filter(trainLocations);
        final Iterable<TrainLocation> filterIPLocations = Iterables.filter(recentlySeenTrackLocations, t -> {
            final String yLocation = IP_LOCATION_FILTER_PRECISION.format(t.location.getY());
            boolean isIPLocation = (yLocation.equals("60,170799") || yLocation.equals("60,170800")) && IP_LOCATION_FILTER_PRECISION.format(
                    t.location.getX()).equals("24,937500");

            if (isIPLocation) {
                log.info("Found IP location for {} ({} / {})", t, t.location, t.liikeLocation);
            }

            return !isIPLocation;
        });

        final Iterable<TrainLocation> filterLocationsOutsideTracks = Iterables.filter(filterIPLocations,
                t -> trainLocationNearTrackFilterService.isTrainLocationNearTrack(t));

        final ArrayList<TrainLocation> result = Lists.newArrayList(filterLocationsOutsideTracks);
        return result;
    }
}
