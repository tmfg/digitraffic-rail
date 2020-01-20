package fi.livi.rata.avoindata.common.domain.trackwork;

import com.vividsolutions.jts.geom.Geometry;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class TrackWorkNotification {

    @EmbeddedId
    public TrackWorkNotificationId id;

    @ApiModelProperty("State")
    public TrackWorkNotificationState state;

    @ApiModelProperty("Which organization created this notification")
    public String organization;

    @ApiModelProperty("When this notification was created")
    public ZonedDateTime created;

    @ApiModelProperty("When this notification last modified")
    public ZonedDateTime modified;

    @ApiModelProperty("Does the notification contain a traffic safety plan")
    public Boolean trafficSafetyPlan;

    @ApiModelProperty("Does the notification contain a speed limit removal plan")
    public Boolean speedLimitRemovalPlan;

    @ApiModelProperty("Does the notification contain a electricity safety plan")
    public Boolean electricitySafetyPlan;

    @ApiModelProperty("Does the notification contain a speed limit plan")
    public Boolean speedLimitPlan;

    @ApiModelProperty("Does the notification contain a plan for persons in charge")
    public Boolean personInChargePlan;

    @ApiModelProperty("Approximate location on map")
    public Geometry locationMap;

    @ApiModelProperty("Approximate location in schema")
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

    @ApiModelProperty("Id")
    public Long getId() {
        return id.id;
    }

    @ApiModelProperty("Version")
    public Long getVersion() {
        return id.version;
    }

    @Embeddable
    public static class TrackWorkNotificationId implements Serializable {
        @Column(name = "id")
        public Long id;
        @Column(name = "version")
        public Long version;

        public TrackWorkNotificationId() {
            // for Hibernate
        }

        public TrackWorkNotificationId(final Long id, final Long version) {
            this.id = id;
            this.version = version;
        }
    }


}
