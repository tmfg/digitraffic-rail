package fi.livi.rata.avoindata.common.domain.train;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Type;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;

import edu.umd.cs.findbugs.annotations.Nullable;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView.LiveTrains;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView.ScheduleTrains;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(indexes = {
        @Index(name = "timetablerow_stationShortCode", columnList = "stationShortCode")
})
@Schema(name = "TimeTableRow", title = "TimeTableRow", description = "A part of train's schedule")
public class TimeTableRow {
    public enum TimeTableRowType {
        ARRIVAL,
        DEPARTURE
    }
    public enum EstimateSourceEnum {
        LIIKE_USER,
        MIKU_USER,
        LIIKE_AUTOMATIC,
        UNKNOWN,
        COMBOCALC
    }

    @EmbeddedId
    @JsonIgnore
    public TimeTableRowId id;

    @JsonUnwrapped
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    public StationEmbeddable station;

    @Column(nullable = false)
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    public TimeTableRowType type ;

    @Schema(description = "Does the train actual stop on the station")
    public boolean trainStopping = true;

    @Column
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    // Should be null when trainStopping == false
    @Schema(description = "Is the stop 'commercial' ie. loading/unloading of passengers or cargo")
    public Boolean commercialStop;

    @Column
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    @Schema(description = "Track where the train stops",example = "1")
    public String commercialTrack;

    @Column(nullable = false)
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    @Schema(description = "Is the schedule part cancelled")
    public boolean cancelled;

    @Column
    @JsonView({LiveTrains.class, ScheduleTrains.class})
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    @Schema(description = "Scheduled time for departure/arrival of the train")
    public ZonedDateTime scheduledTime;

    @Column
    @JsonView(LiveTrains.class)
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    @Schema(description = "Estimated time for departure/arrival of the train")
    public ZonedDateTime liveEstimateTime;

    @JsonView(LiveTrains.class)
    @Schema(description = "Source for the estimate",example = "LIIKE_USER")
    public EstimateSourceEnum estimateSource;

    @JsonView({LiveTrains.class, ScheduleTrains.class})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    // Should be null when trainStopping == false
    @Schema(description = "Set if the train is delayed, but it is impossible to estimate for how long")
    public Boolean unknownDelay;

    @Nullable
    @Column
    @JsonView(LiveTrains.class)
    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @Schema(description = "Actual time when train departured or arrived on the station")
    public ZonedDateTime actualTime;

    @Column
    @JsonView(LiveTrains.class)
    @Schema(description = "Difference between schedule and actual time in minutes", example = "5")
    public Long differenceInMinutes;

    @Column
    @JsonIgnore
    private ZonedDateTime commercialTrackChanged;

    @OneToMany(mappedBy = "timeTableRow", fetch = FetchType.LAZY)
    @JsonView(LiveTrains.class)
    public Set<Cause> causes = new HashSet<>();

    @OneToMany(mappedBy = "timeTableRow", fetch = FetchType.LAZY)
    @JsonIgnore
    public Set<TrainReady> trainReadies = new HashSet<>();

    @JsonView(LiveTrains.class)
    @JsonProperty("trainReady")
    public TrainReady getTrainReady() {
        if (trainReadies == null || trainReadies.isEmpty()) {
            return null;
        }
        else {
            return trainReadies.iterator().next();
        }
    }

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "trainNumber", referencedColumnName = "trainNumber", nullable = false, insertable = false, updatable = false)})
    @JsonIgnore
    public Train train;

    @Transient
    @JsonIgnore
    public Long version;


    protected TimeTableRow() {
    }

    public TimeTableRow(final String stationShortCode, final int stationcUICCode, final String countryCode, final TimeTableRowType type,
                        final String commercialTrack, final boolean cancelled, final ZonedDateTime scheduledTime, final ZonedDateTime liveEstimateTime,
                        final ZonedDateTime actualTime, final Long differenceInMinutes, final long attapId, final long trainNumber,
                        final LocalDate departureDate, final Boolean commercialStop, final long version, Set<TrainReady> trainReadies, EstimateSourceEnum estimateSource, ZonedDateTime commercialTrackChanged) {
        id = new TimeTableRowId(attapId, departureDate, trainNumber);
        this.station = new StationEmbeddable(stationShortCode, stationcUICCode, countryCode);
        this.type = type;
        this.commercialTrack = commercialTrack;
        this.cancelled = cancelled;
        this.scheduledTime = scheduledTime;
        this.liveEstimateTime = liveEstimateTime;
        this.actualTime = actualTime;
        this.differenceInMinutes = differenceInMinutes;
        this.commercialStop = commercialStop;
        this.version = version;
        this.trainReadies = trainReadies;
        this.estimateSource = estimateSource;
        this.commercialTrackChanged = commercialTrackChanged;
    }

    @Override
    public String toString() {
        return String.format("%s: %s (%s)", scheduledTime, station.stationShortCode, type);
    }

    public static TimeTableRowId getIdDirect(TimeTableRow timeTableRow) {
        if (timeTableRow instanceof HibernateProxy) {
            LazyInitializer lazyInitializer = ((HibernateProxy) timeTableRow).getHibernateLazyInitializer();
            if (lazyInitializer.isUninitialized()) {
                return (TimeTableRowId) lazyInitializer.getIdentifier();
            }
        }
        return timeTableRow.id;
    }

    @Hidden
    public ZonedDateTime getCommercialTrackChanged() {
        return commercialTrackChanged;
    }

    public void setCommercialTrackChanged(ZonedDateTime value) {
        commercialTrackChanged = value;
    }
}
