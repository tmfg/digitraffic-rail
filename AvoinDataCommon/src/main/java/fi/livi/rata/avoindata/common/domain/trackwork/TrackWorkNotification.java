package fi.livi.rata.avoindata.common.domain.trackwork;

import java.time.ZonedDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

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
    public String parts;
}
