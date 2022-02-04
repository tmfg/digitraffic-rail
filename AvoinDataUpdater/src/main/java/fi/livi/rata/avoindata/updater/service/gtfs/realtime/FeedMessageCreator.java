package fi.livi.rata.avoindata.updater.service.gtfs.realtime;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSTripService.TRIP_REPLACEMENT;

public class FeedMessageCreator {
    private final TripFinder tripFinder;

    private static Logger log = LoggerFactory.getLogger(FeedMessageCreator.class);

    public FeedMessageCreator(final List<GTFSTrip> trips) {
        this.tripFinder = new TripFinder(trips);
    }

    private static GtfsRealtime.FeedMessage.Builder createBuilderWithHeader() {
        return GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder()
                        .setGtfsRealtimeVersion("2.0")
                        .setTimestamp(new Date().getTime() / 1000)
                        .build());
    }

    public GtfsRealtime.FeedMessage createFeedMessage(final List<TrainLocation> locations) {
        return createBuilderWithHeader()
                .addAllEntity(createEntities(locations))
                .build();
    }

    private static String createTripId(final TrainLocation location) {
        return String.format("%d_location_%d", location.trainLocationId.trainNumber, location.id);
    }

    private List<GtfsRealtime.FeedEntity> createEntities(final List<TrainLocation> locations) {
        return locations.stream().map(this::createFeedEntity)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private GtfsRealtime.FeedEntity createFeedEntity(final TrainLocation location) {
        final GTFSTrip trip = tripFinder.find(location);

        return trip == null ? null : GtfsRealtime.FeedEntity.newBuilder()
                .setId(createTripId(location))
                .setVehicle(GtfsRealtime.VehiclePosition.newBuilder()
                        .setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                                .setRouteId(trip.routeId)
                                .setTripId(trip.tripId)
                                .setStartDate(location.trainLocationId.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                                .build())
                        .setPosition(GtfsRealtime.Position.newBuilder()
                                .setLatitude((float)location.location.getY())
                                .setLongitude((float)location.location.getX())
                                .setSpeed(location.speed)
                                .build())
                        .build())
                .build();
    }

    static class TripFinder {
        private final List<GTFSTrip> trips;

        TripFinder(final List<GTFSTrip> trips) {
            this.trips = trips;
        }

        GTFSTrip find(final TrainLocation location) {
            final List<GTFSTrip> filtered = trips.stream()
                    .filter(t -> t.id.trainNumber.equals(location.trainLocationId.trainNumber))
                    .filter(t -> !t.id.startDate.isAfter(location.trainLocationId.departureDate))
                    .filter(t -> !t.id.endDate.isBefore(location.trainLocationId.departureDate))
                    .collect(Collectors.toList());

            if(filtered.isEmpty()) {
                log.info("Could not find trip for trainnumber " + location.trainLocationId.trainNumber);
                return null;
            }

            if(filtered.size() > 1) {
                final Optional<GTFSTrip> replacement = findReplacement(filtered);

                if(replacement.isEmpty()) {
                    log.info("Multiple trips:" + filtered);
                    log.error("Could not find replacement from multiple " + location.trainLocationId.trainNumber);
                }

                return replacement.orElse(null);
            }

            return filtered.get(0);
        }

        Optional<GTFSTrip> findReplacement(final List<GTFSTrip> trips) {
            return trips.stream()
                    .filter(trip -> trip.tripId.endsWith(TRIP_REPLACEMENT))
                    .findFirst();
        }
    }
}
