package fi.livi.rata.avoindata.common.domain.trackwork;

import javax.persistence.*;

@Embeddable
public class SpeedLimit {

    public Integer speed;
    public boolean signs;
    public boolean balises;

}
