package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.time.LocalDate;
import java.time.LocalDateTime;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;

/// A [GTFSTrainLocation] whose stop fields (station/track/delay/vehicle-at-stop) are reported from a train's
/// arrived terminus (see [fi.livi.rata.avoindata.updater.service.siri.common.CommercialStopVisits
/// #resolveTerminusFallback]), for the SIRI-VM-only case where `GTFSTrainRepository.getTrainLocations`'s own
/// "next stop" query resolved nothing (a `null` `stationShortCode`) because the train has already arrived at
/// its terminus and has no further stop to report - see that query's javadoc for why it deliberately leaves
/// this case unresolved rather than resolving it in SQL. Position/speed/timestamp/id fields are passed through
/// unchanged from the original, SQL-resolved location; only the stop-related fields are overridden here.
final class TerminusFallbackTrainLocation implements GTFSTrainLocation {

    private final GTFSTrainLocation delegate;
    private final GTFSTimeTableRow terminusRow;

    TerminusFallbackTrainLocation(final GTFSTrainLocation delegate, final GTFSTimeTableRow terminusRow) {
        this.delegate = delegate;
        this.terminusRow = terminusRow;
    }

    @Override
    public long getId() {
        return delegate.getId();
    }

    @Override
    public LocalDate getDepartureDate() {
        return delegate.getDepartureDate();
    }

    @Override
    public long getTrainNumber() {
        return delegate.getTrainNumber();
    }

    @Override
    public LocalDateTime getTimestampUtc() {
        return delegate.getTimestampUtc();
    }

    @Override
    public double getX() {
        return delegate.getX();
    }

    @Override
    public double getY() {
        return delegate.getY();
    }

    @Override
    public int getSpeed() {
        return delegate.getSpeed();
    }

    @Override
    public Integer getAccuracy() {
        return delegate.getAccuracy();
    }

    @Override
    public String getStationShortCode() {
        return terminusRow.stationShortCode;
    }

    @Override
    public String getCommercialTrack() {
        return terminusRow.commercialTrack;
    }

    @Override
    public Boolean getUnknownTrack() {
        return terminusRow.unknownTrack;
    }

    @Override
    public Boolean getUnknownDelay() {
        return terminusRow.unknownDelay;
    }

    @Override
    public Integer getDelaySeconds() {
        return terminusRow.delayInSeconds();
    }

    @Override
    public Integer getVehicleAtStopValue() {
        // The train has arrived at (and is dwelling at) its terminus by definition - see
        // CommercialStopVisits#resolveTerminusFallback.
        return 1;
    }
}
