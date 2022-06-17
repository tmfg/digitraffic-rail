package fi.livi.rata.avoindata.common.domain.cause;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(description = "Details why a train is not on schedule. Train-responses only have ids and codes populated.")
public class Cause {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @ManyToOne
    @JoinColumns({@JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false), @JoinColumn(name =
            "trainNumber", referencedColumnName = "trainNumber", nullable = false), @JoinColumn(name = "attapId", referencedColumnName =
            "attapId", nullable = false)})
    @JsonIgnore
    public TimeTableRow timeTableRow;

    @JsonIgnore
    @Transient
    public long version;

    @ManyToOne
    @JsonUnwrapped
    public CategoryCode categoryCode;

    @ManyToOne
    @JsonUnwrapped
    public DetailedCategoryCode detailedCategoryCode;

    @ManyToOne
    @JsonUnwrapped
    public ThirdCategoryCode thirdCategoryCode;

    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Integer getCategoryCodeId() throws GSSException {
        if (categoryCode != null) {
            return causeOidToNumber(categoryCode.oid);
        }
        return null;
    }

    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Integer getDetailedCategoryCodeId() throws GSSException {
        if (detailedCategoryCode != null) {
            return causeOidToNumber(detailedCategoryCode.oid);
        }
        return null;
    }


    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Integer getThirdCategoryCodeId() throws GSSException {
        if (thirdCategoryCode != null) {
            return  causeOidToNumber(thirdCategoryCode.oid);
        }
        return null;
    }

    public static int causeOidToNumber(String oid) throws GSSException {
        Oid oidObject = new Oid(oid);
        byte[] oidObjectDER = oidObject.getDER();
        byte[] intBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
             intBytes[i] = oidObjectDER[oidObjectDER.length-1-i];
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(intBytes);
        return byteBuffer.getInt();
    }
}
