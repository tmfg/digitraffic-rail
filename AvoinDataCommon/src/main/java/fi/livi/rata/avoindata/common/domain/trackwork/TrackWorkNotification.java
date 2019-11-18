package fi.livi.rata.avoindata.common.domain.trackwork;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

@Entity
public class TrackWorkNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Integer rumaId;
    public Integer rumaVersion;
    public TrackWorkNotificationState state;
    public String organization;
    public ZonedDateTime created;
    public ZonedDateTime modified;
    public Boolean trafficSafetyPlan;
    public Boolean speedLimitRemovalPlan;
    public Boolean electricitySafetyPlan;
    public Boolean speedLimitPlan;
    public Boolean personInChargePlan;

    @OneToMany(mappedBy = "trackWorkNotification", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    public Set<TrackWorkPart> trackWorkParts = new HashSet<>();
}
