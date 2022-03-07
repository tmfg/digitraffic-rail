package fi.livi.rata.avoindata.updater.service.gtfs.realtime;

import com.google.transit.realtime.GtfsRealtime;
import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSTripService.TRIP_REPLACEMENT;

@Service
public class FeedMessageService {
    private static final Logger log = LoggerFactory.getLogger(FeedMessageService.class);

    private final GTFSTripRepository gtfsTripRepository;

    public FeedMessageService(final GTFSTripRepository gtfsTripRepository) {
        this.gtfsTripRepository = gtfsTripRepository;
    }

    private static GtfsRealtime.FeedMessage.Builder createBuilderWithHeader() {
        return GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder()
                        .setGtfsRealtimeVersion("2.0")
                        .setTimestamp(new Date().getTime() / 1000)
                        .build());
    }

    public GtfsRealtime.FeedMessage createVehicleLocationFeedMessage(final List<TrainLocation> locations) {
        final TripFinder tripFinder = new TripFinder(gtfsTripRepository.findAll());

        return createBuilderWithHeader()
                .addAllEntity(createVLEntities(tripFinder, locations))
                .build();
    }

    private static String createVesselLocationId(final TrainLocation location) {
        return String.format("%d_location_%d", location.trainLocationId.trainNumber, location.id);
    }

    private static String createCancellationId(final GTFSTrain train) {
        return String.format("%d_cancel_%s", train.id.trainNumber, train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static String createTripUpdateId(final GTFSTrain train) {
        return String.format("%d_update_%d", train.id.trainNumber, train.version);
    }

    private List<GtfsRealtime.FeedEntity> createVLEntities(final TripFinder tripFinder, final List<TrainLocation> locations) {
        return locations.stream().map(location -> createVLEntity(tripFinder, location))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private GtfsRealtime.FeedEntity createVLEntity(final TripFinder tripFinder, final TrainLocation location) {
        final GTFSTrip trip = tripFinder.find(location);

        return trip == null ? null : GtfsRealtime.FeedEntity.newBuilder()
                .setId(createVesselLocationId(location))
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

    private GtfsRealtime.FeedEntity createTUCancelledEntity(final GTFSTrip trip, final GTFSTrain train) {
        final GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                        .setRouteId(trip.routeId)
                        .setTripId(trip.tripId)
                        .setStartDate(train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
                        .build())
                .build();

        return GtfsRealtime.FeedEntity.newBuilder()
                .setId(createCancellationId(train))
                .setTripUpdate(tripUpdate)
                .build();
    }

    private boolean isInThePast(final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {
        final ZonedDateTime limit = ZonedDateTime.now().plusMinutes(2);

        if(arrival != null && arrival.liveEstimateTime != null) {
            if(arrival.liveEstimateTime.isAfter(limit) || arrival.scheduledTime.isAfter(limit)) {
                return false;
            }
        }

        if(departure != null && departure.liveEstimateTime != null) {
            return !departure.liveEstimateTime.isAfter(limit) && !departure.scheduledTime.isAfter(limit);
        }

        return true;
    }

    private GtfsRealtime.TripUpdate.StopTimeUpdate createStopTimeUpdate(final int stopSequence, final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure, final boolean updatesEmpty) {
        final Long arrivalDifference = arrival == null ? null : arrival.differenceInMinutes;
        final Long departureDifference = departure == null ? null : departure.differenceInMinutes;

        // it's in the past, don't report it!
        if(isInThePast(arrival, departure)) {
//            System.out.println("row is in the past!");
            return null;
        }

        // it's not late, don't report it!
        // must report first stop though
        if(!updatesEmpty && arrivalDifference != null && arrivalDifference == 0 && (departureDifference == null || departureDifference == 0)) {
            return null;
        }

        final String stopId = arrival == null ? departure.stationShortCode : arrival.stationShortCode;
        final GtfsRealtime.TripUpdate.StopTimeUpdate.Builder builder = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                .setStopId(stopId)
                .setStopSequence(stopSequence);

        // GTFS delay is seconds, our difference is minutes
        if(arrivalDifference != null) {
            builder.setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay((int)(arrivalDifference * 60))
                    .build());
        }
        if(departureDifference != null) {
            builder.setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay((int)(departureDifference * 60))
                    .build());
        }

        return builder.build();
    }

    private boolean delaysDiffer(final GtfsRealtime.TripUpdate.StopTimeUpdate previous, @NonNull final GtfsRealtime.TripUpdate.StopTimeUpdate current) {
        if (previous == null) {
            return true;
        }

        final int previousDelay = previous.getDeparture().getDelay();

        if (current.hasArrival() && current.getArrival().getDelay() != previousDelay) {
            return true;
        }

        return current.hasDeparture() && current.getDeparture().getDelay() != previousDelay;
    }

    private List<GtfsRealtime.TripUpdate.StopTimeUpdate> createStopTimeUpdates(final GTFSTrain train) {
        final List<GtfsRealtime.TripUpdate.StopTimeUpdate> updates = new ArrayList<>();
        int stopSequence = 0;

        GtfsRealtime.TripUpdate.StopTimeUpdate previous = createStopTimeUpdate(stopSequence++, null, train.timeTableRows.get(1), true);
        if(previous != null) {
//            System.out.println("train " + train.id.trainNumber + " adding stop" + (stopSequence - 1));
            updates.add(previous);
        }

        for(int i = 1; i < train.timeTableRows.size();stopSequence++) {
            final GTFSTimeTableRow arrival = train.timeTableRows.get(i++);
            final GTFSTimeTableRow departure = train.timeTableRows.size() == i ? null : train.timeTableRows.get(i++);

            final GtfsRealtime.TripUpdate.StopTimeUpdate current = createStopTimeUpdate(stopSequence, arrival, departure, updates.isEmpty());

            if(current != null && delaysDiffer(previous, current)) {
                updates.add(current);
            }

            previous = current;
        }

        return updates;
    }

    private GtfsRealtime.FeedEntity createTUUpdateEntity(final GTFSTrip trip, final GTFSTrain train) {
        final List<GtfsRealtime.TripUpdate.StopTimeUpdate> stopTimeUpdates = createStopTimeUpdates(train);

        if(stopTimeUpdates.isEmpty()) {
            return null;
        }

        final GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                        .setRouteId(trip.routeId)
                        .setTripId(trip.tripId)
                        .setStartDate(train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .build())
                .addAllStopTimeUpdate(stopTimeUpdates)
                .build();

        return GtfsRealtime.FeedEntity.newBuilder()
                .setId(createTripUpdateId(train))
                .setTripUpdate(tripUpdate)
                .build();
    }

    public GtfsRealtime.FeedEntity createTUEntity(final TripFinder tripFinder, final GTFSTrain train) {
        final GTFSTrip trip = tripFinder.find(train);

        if(trip != null) {
            if (train.cancelled) {
                return createTUCancelledEntity(trip, train);
            }

            if (trip.version != train.version) {
                return createTUUpdateEntity(trip, train);
            }
        }

        // new train, we do nothing.  the realtime-spec does not support creation of new trips!
        return null;
    }

    public List<GtfsRealtime.FeedEntity> createTUEntities(final TripFinder tripFinder, final List<GTFSTrain> trains) {
        return trains.stream()
                .map(train -> createTUEntity(tripFinder, train))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public GtfsRealtime.FeedMessage createTripUpdateFeedMessage(final List<GTFSTrain> trains) {
        final TripFinder tripFinder = new TripFinder(gtfsTripRepository.findAll());

        return createBuilderWithHeader()
                .addAllEntity(createTUEntities(tripFinder, trains))
                .build();
    }

    static class TripFinder {
        private final Map<Long, List<GTFSTrip>> tripMap = new HashMap<>();

        TripFinder(final List<GTFSTrip> trips) {
            trips.forEach(t -> {
                tripMap.putIfAbsent(t.id.trainNumber, new ArrayList<>());
                tripMap.get(t.id.trainNumber).add(t);
            });
        }

        Stream<GTFSTrip> safeStream(final List<GTFSTrip> trips) {
            return trips == null ? Stream.empty() : trips.stream();
        }

        GTFSTrip find(final GTFSTrain train) {
            final List<GTFSTrip> trips = tripMap.get(train.id.trainNumber);

            final List<GTFSTrip> filtered = safeStream(trips)
                    .filter(t -> t.id.trainNumber.equals(train.id.trainNumber))
                    .filter(t -> !t.id.startDate.isAfter(train.id.departureDate))
                    .filter(t -> !t.id.endDate.isBefore(train.id.departureDate))
                    .collect(Collectors.toList());

            return filtered.get(0);
        }

        GTFSTrip find(final TrainLocation location) {
            final List<GTFSTrip> trips = tripMap.get(location.trainLocationId.trainNumber);

            final List<GTFSTrip> filtered = safeStream(trips)
                    .filter(t -> t.id.trainNumber.equals(location.trainLocationId.trainNumber))
                    .filter(t -> !t.id.startDate.isAfter(location.trainLocationId.departureDate))
                    .filter(t -> !t.id.endDate.isBefore(location.trainLocationId.departureDate))
                    .collect(Collectors.toList());

            if(filtered.isEmpty()) {
                log.trace("Could not find trip for train number " + location.trainLocationId.trainNumber);
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
