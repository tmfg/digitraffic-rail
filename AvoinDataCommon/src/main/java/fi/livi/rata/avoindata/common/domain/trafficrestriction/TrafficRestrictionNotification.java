package fi.livi.rata.avoindata.common.domain.trafficrestriction;

import com.vividsolutions.jts.geom.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class TrafficRestrictionNotification {

    @EmbeddedId
    public TrafficRestrictionNotificationId id;

    public TrafficRestrictionNotificationState state;
    public String organization;
    public ZonedDateTime created;
    public ZonedDateTime modified;
    public TrafficRestrictionType limitation;
    public String limitationDescription;
    public String extraInfo;
    public String twnId;
    public Double axleWeightMax;
    public ZonedDateTime startDate;
    public ZonedDateTime endDate;
    public ZonedDateTime finished;
    public Geometry locationMap;
    public Geometry locationSchema;

    public TrafficRestrictionNotification(
            final TrafficRestrictionNotificationId id,
            final TrafficRestrictionNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final TrafficRestrictionType limitation,
            final String limitationDescription,
            final String extraInfo,
            final String twnId,
            final Double axleWeightMax,
            final ZonedDateTime startDate,
            final ZonedDateTime endDate,
            final ZonedDateTime finished,
            final Geometry locationMap,
            final Geometry locationSchema
    ) {
        this.id = id;
        this.state = state;
        this.organization = organization;
        this.created = created;
        this.modified = modified;
        this.limitation = limitation;
        this.limitationDescription = limitationDescription;
        this.extraInfo = extraInfo;
        this.twnId = twnId;
        this.axleWeightMax = axleWeightMax;
        this.startDate = startDate;
        this.endDate = endDate;
        this.finished = finished;
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
    }

    public TrafficRestrictionNotification() {
        // for Hibernate
    }

    public Long getId() {
        return id.id;
    }

    public Long getVersion() {
        return id.version;
    }

    @OneToMany(mappedBy = "trafficRestrictionNotification", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    public Set<RumaLocation> locations = new HashSet<>();

    @Embeddable
    public static class TrafficRestrictionNotificationId implements Serializable {
        @Column(name = "id")
        public Long id;
        @Column(name = "version")
        public Long version;

        public TrafficRestrictionNotificationId() {
            // for Hibernate
        }

        public TrafficRestrictionNotificationId(final Long id, final Long version) {
            this.id = id;
            this.version = version;
        }
    }

}
