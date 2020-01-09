package fi.livi.rata.avoindata.common.domain.trackwork;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

@Entity
public class TrackWorkNotification {

    @EmbeddedId
    public TrackWorkNotificationId id;
    public TrackWorkNotificationState state;
    public String organization;
    public ZonedDateTime created;
    public ZonedDateTime modified;
    public Boolean trafficSafetyPlan;
    public Boolean speedLimitRemovalPlan;
    public Boolean electricitySafetyPlan;
    public Boolean speedLimitPlan;
    public Boolean personInChargePlan;

    public TrackWorkNotification(
            final TrackWorkNotificationId id,
            final TrackWorkNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final Boolean trafficSafetyPlan,
            final Boolean speedLimitPlan,
            final Boolean speedLimitRemovalPlan,
            final Boolean electricitySafetyPlan,
            final Boolean personInChargePlan
    ) {
        this.id = id;
        this.state = state;
        this.organization = organization;
        this.created = created;
        this.modified = modified;
        this.trafficSafetyPlan = trafficSafetyPlan;
        this.speedLimitPlan = speedLimitPlan;
        this.speedLimitRemovalPlan = speedLimitRemovalPlan;
        this.electricitySafetyPlan = electricitySafetyPlan;
        this.personInChargePlan = personInChargePlan;
    }

    public TrackWorkNotification() {
        // for Hibernate
    }

    @OneToMany(mappedBy = "trackWorkNotification", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    public Set<TrackWorkPart> trackWorkParts = new HashSet<>();

    public Integer getId() {
        return id.id;
    }

    public Integer getVersion() {
        return id.version;
    }

    @Embeddable
    public static class TrackWorkNotificationId implements Serializable {
        @Column(name = "id")
        public Integer id;
        @Column(name = "version")
        public Integer version;

        public TrackWorkNotificationId() {
            // for Hibernate
        }

        public TrackWorkNotificationId(final Integer id, final Integer version) {
            this.id = id;
            this.version = version;
        }
    }


}
