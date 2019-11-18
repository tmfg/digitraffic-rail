package fi.livi.rata.avoindata.common.domain.trackwork;

import javax.persistence.*;

@Embeddable
public class SpeedLimit {

    public Integer speed;
    public boolean signs;
    public boolean balises;

    public SpeedLimit(Integer speed, boolean signs, boolean balises) {
        this.speed = speed;
        this.signs = signs;
        this.balises = balises;
    }
}
