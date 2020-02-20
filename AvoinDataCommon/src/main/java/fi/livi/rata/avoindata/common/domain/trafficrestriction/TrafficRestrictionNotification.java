package fi.livi.rata.avoindata.common.domain.trafficrestriction;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.io.Serializable;
import java.time.ZonedDateTime;

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
    public Double axleWeightMax;
    public ZonedDateTime startDate;
    public ZonedDateTime endDate;

    /*


        @Description("Rajoite")
        val rajoite: ApiRajoiteDto,

        @Description("LR-ilmoituksen lisätiedot")
        val lisatiedot: String,

        @Description("Ratatyöilmoituksen ID, jos pohjautuu sellaiseen")
        val ratatyoilmoitusId: RtiId?,

        @Description("Kohteet rakenteellisessa muodossa")
        val kohteet: List<OpenKohdeDto>,

        @Description("Rajoitteen päättymispäivä")
        val finished: Instant?



        data class ApiRajoiteDto(

        @Description("Rajoitteen tyyppi")
        val tyyppi: fi.livi.ruma.core.lri.RajoiteTyyppi,

        @Description("Rajoitteen nimi")
        val nimi: String,

        @Description("Rajoitteen kuvaus")
        val rajoiteKuvaus: String?,

        @Description("Akselipaino maksimi tonnia (sallii desimaalit)")
        val akselipainoMaxFloat: Float?
) {
        // TODO can be removed once all old clients are deprecated
        @Description("Akselipaino maksimi tonnia")
        val akselipainoMax: Int? = akselipainoMaxFloat?.toInt()
}
     */

    public TrafficRestrictionNotification(
            final TrafficRestrictionNotificationId id,
            final TrafficRestrictionNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final TrafficRestrictionType limitation,
            final String limitationDescription,
            final String extraInfo,
            final Double axleWeightMax,
            final ZonedDateTime startDate,
            final ZonedDateTime endDate
    ) {
        this.id = id;
        this.state = state;
        this.organization = organization;
        this.created = created;
        this.modified = modified;
        this.limitation = limitation;
        this.limitationDescription = limitationDescription;
        this.extraInfo = extraInfo;
        this.axleWeightMax = axleWeightMax;
        this.startDate = startDate;
        this.endDate = endDate;
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
