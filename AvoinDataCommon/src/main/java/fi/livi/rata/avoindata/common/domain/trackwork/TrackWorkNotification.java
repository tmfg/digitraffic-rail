package fi.livi.rata.avoindata.common.domain.trackwork;


import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Geometry;

@Entity
public class TrackWorkNotification {

    @EmbeddedId
    public TrackWorkNotificationId id;

    @Enumerated(EnumType.ORDINAL)
    public TrackWorkNotificationState state;
    public String organization;
    public ZonedDateTime created;
    public ZonedDateTime modified;
    public Boolean trafficSafetyPlan;
    public Boolean speedLimitRemovalPlan;
    public Boolean electricitySafetyPlan;
    public Boolean speedLimitPlan;
    public Boolean personInChargePlan;
    public Geometry locationMap;
    public Geometry locationSchema;

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
            final Boolean personInChargePlan,
            final Geometry locationMap,
            final Geometry locationSchema
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
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
    }

    public TrackWorkNotification() {
        // for Hibernate
    }

    @OrderBy("part_index")
    @OneToMany(mappedBy = "trackWorkNotification", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    public Set<TrackWorkPart> trackWorkParts = new HashSet<>();

    public String getId() {
        return id.id;
    }

    public Long getVersion() {
        return id.version;
    }

    @Embeddable
    public static class TrackWorkNotificationId implements Serializable {
        @Column(name = "id")
        public String id;
        @Column(name = "version")
        public Long version;

        public TrackWorkNotificationId() {
            // for Hibernate
        }

        public TrackWorkNotificationId(final String id, final Long version) {
            this.id = id;
            this.version = version;
        }
    }


}
