package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;

/** A simple in-memory {@link GTFSTrainLocation} for tests — the interface is a JPA projection in production. */
record TestGTFSTrainLocation(long id, LocalDate departureDate, long trainNumber, ZonedDateTime timestamp,
                              double x, double y, int speed, int accuracy, String stationShortCode,
                              String commercialTrack, Boolean unknownTrack, Integer delaySeconds,
                              Boolean vehicleAtStop)
        implements GTFSTrainLocation {

    @Override
    public long getId() {
        return id;
    }

    @Override
    public LocalDate getDepartureDate() {
        return departureDate;
    }

    @Override
    public long getTrainNumber() {
        return trainNumber;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public int getSpeed() {
        return speed;
    }

    @Override
    public int getAccuracy() {
        return accuracy;
    }

    @Override
    public String getStationShortCode() {
        return stationShortCode;
    }

    @Override
    public String getCommercialTrack() {
        return commercialTrack;
    }

    @Override
    public Boolean getUnknownTrack() {
        return unknownTrack;
    }

    @Override
    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    @Override
    public Boolean getVehicleAtStop() {
        return vehicleAtStop;
    }

    @Override
    public Integer getVehicleAtStopValue() {
        // Not used: getVehicleAtStop() is overridden directly above, bypassing the default method that would
        // otherwise call this.
        throw new UnsupportedOperationException();
    }
}
