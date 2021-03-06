package fi.livi.rata.avoindata.common.domain.train;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import edu.umd.cs.findbugs.annotations.Nullable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.time.ZonedDateTime;

@Entity
public class LiveTimeTableTrain {
    @EmbeddedId
    @JsonUnwrapped
    public TrainId id;

    @JsonIgnore
    public Long version;

    @JsonUnwrapped
    @JsonView({TrainJsonView.LiveTrains.class, TrainJsonView.ScheduleTrains.class})
    public String stationShortCode;

    public boolean trainStopping;

    @JsonView({TrainJsonView.LiveTrains.class, TrainJsonView.ScheduleTrains.class})
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime scheduledTime;

    @JsonView(TrainJsonView.LiveTrains.class)
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime liveEstimateTime;

    @Nullable
    @JsonView(TrainJsonView.LiveTrains.class)
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime actualTime;

    @JsonIgnore
    public Long trainCategoryId;

    @Column(nullable = false)
    @JsonView({TrainJsonView.LiveTrains.class, TrainJsonView.ScheduleTrains.class})
    public TimeTableRow.TimeTableRowType type;
}
