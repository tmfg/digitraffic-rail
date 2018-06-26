package fi.livi.rata.avoindata.common.domain.trainreadymessage;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@Table
@ApiModel(description = "These rules define how TrainRunningMessages trigger actual times for TimeTableRows")
public class TrainRunningMessageRule {
    @Id
    public Long id;

    @ApiModelProperty("For which station is the actual time generated")
    public String timeTableRowStationShortCode;
    @ApiModelProperty("For which TimeTableRow is the actual time generated")
    public TimeTableRow.TimeTableRowType timeTableRowType;

    public String trainRunningMessageStationShortCode;
    public String trainRunningMessageNextStationShortCode;
    public String trainRunningMessageTrackSection;
    public TrainRunningMessageTypeEnum trainRunningMessageType ;

    @ApiModelProperty("TrainRunningMessages timestamp is adjusted for offset when generating actual time")
    public Integer offset;


    @Override
    public String toString() {
        return "TrainRunningMessageRule{" +
                "id=" + id +
                '}';
    }


}